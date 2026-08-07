package com.salonplatform.google;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.LocalCompetitor;
import com.salonplatform.domain.entity.Tenant;
import com.salonplatform.domain.enums.CompetitorType;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.LocalCompetitorRepository;
import com.salonplatform.domain.repository.TenantRepository;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DigitalPresenceSyncService {

    private final GooglePlacesProperties properties;
    private final GooglePlacesClient googlePlacesClient;
    private final BranchRepository branchRepository;
    private final LocalCompetitorRepository localCompetitorRepository;
    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public boolean isPilotBranch(Tenant tenant, Branch branch) {
        return tenant != null
                && branch != null
                && properties.getPilotTenantSlug().equalsIgnoreCase(tenant.getSlug())
                && properties.getPilotBranchCode().equalsIgnoreCase(branch.getCode());
    }

    public Optional<Branch> findPilotBranch(UUID tenantId) {
        return branchRepository.findByTenantId(tenantId).stream()
                .filter(b -> properties.getPilotBranchCode().equalsIgnoreCase(b.getCode()))
                .findFirst();
    }

    @Transactional
    public SyncResult syncPilotBranch(UUID tenantId, int radiusKm, boolean force) {
        if (!properties.isConfigured()) {
            throw new BadRequestException(
                    "Google Places API key is not configured. Set GOOGLE_PLACES_API_KEY on the server.");
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        Branch branch = findPilotBranch(tenantId)
                .orElseThrow(() -> new BadRequestException(
                        "Pilot branch " + properties.getPilotBranchCode() + " not found for this tenant."));

        if (!isPilotBranch(tenant, branch)) {
            throw new BadRequestException("Branch is not enabled for Google pilot sync.");
        }
        if (!force && isFresh(branch.getDigitalPresenceUpdatedAt())) {
            return SyncResult.builder()
                    .skipped(true)
                    .message("Data is fresh — synced within last " + properties.getSyncCacheHours() + " hours.")
                    .branchId(branch.getId())
                    .build();
        }

        requireGeofence(branch);
        int radiusM = radiusKm > 0 ? radiusKm * 1000 : properties.getDefaultRadiusMeters();

        GooglePlaceSnapshot ownListing = enrichSnapshot(resolveOwnListing(branch));
        applySnapshotToBranch(branch, ownListing);

        List<GooglePlaceSnapshot> nearby = googlePlacesClient.searchNearby(
                branch.getLatitude(),
                branch.getLongitude(),
                radiusM,
                15,
                LocalSpotlightKeywords.nearbyPlaceTypes(branch));

        String ownPlaceId = ownListing != null ? ownListing.getPlaceId() : branch.getGooglePlaceId();
        List<GooglePlaceSnapshot> competitors = nearby.stream()
                .filter(p -> ownPlaceId == null || !GooglePlacesClient.normalizePlaceId(ownPlaceId)
                        .equals(GooglePlacesClient.normalizePlaceId(p.getPlaceId())))
                .limit(8)
                .toList();

        deactivateAutoDiscoveredForBranch(tenantId, branch.getId());
        int rivalCount = 0;
        for (GooglePlaceSnapshot rival : competitors) {
            upsertAutoDiscoveredCompetitor(tenantId, branch.getId(), enrichSnapshot(rival));
            rivalCount++;
        }

        Map<String, Integer> ranks = new LinkedHashMap<>();
        List<GoogleSearchRankEntry> rankEntries = new ArrayList<>();
        List<String> searchKeywords = LocalSpotlightKeywords.searchKeywords(branch);
        for (String keyword : searchKeywords) {
            GooglePlacesClient.TextSearchInsight insight = googlePlacesClient.analyzeTextSearch(
                    keyword,
                    ownPlaceId,
                    branch.getLatitude(),
                    branch.getLongitude(),
                    radiusM);
            if (insight.rank() > 0) {
                ranks.put(keyword, insight.rank());
            }
            rankEntries.add(GoogleSearchRankEntry.builder()
                    .keyword(keyword)
                    .yourRank(insight.rank() > 0 ? insight.rank() : null)
                    .yourRankBeyondTop20(insight.rank() <= 0)
                    .topThreePlaces(insight.topPlaces())
                    .build());
        }
        Integer bestRank = ranks.values().stream().min(Integer::compareTo).orElse(null);
        if (bestRank != null && bestRank > 0) {
            branch.setEstimatedSearchRank(bestRank);
        }
        branch.setGoogleSearchRankData(writeRankEntries(rankEntries));

        branch.setDigitalPresenceUpdatedAt(Instant.now());
        branchRepository.save(branch);

        log.info("Google sync complete for branch {} — ownMatch={}, rivals={}", branch.getCode(), ownListing != null, rivalCount);

        return SyncResult.builder()
                .skipped(false)
                .branchId(branch.getId())
                .branchName(branch.getName())
                .ownListingMatched(ownListing != null)
                .ownListingName(ownListing != null ? ownListing.getName() : null)
                .googleMapsUrl(branch.getGoogleMapsUrl())
                .googleFormattedAddress(ownListing != null ? ownListing.getFormattedAddress() : branch.getAddress())
                .rivalsSynced(rivalCount)
                .searchRanks(ranks)
                .message(ownListing != null
                        ? "Synced from Google Places — matched \"" + ownListing.getName() + "\", "
                                + rivalCount + " nearby businesses within ~" + radiusKm + " km."
                        : "Synced " + rivalCount + " nearby rivals, but could not match your Google listing. "
                                + "Check branch name vs Google Business Profile name, then refresh.")
                .syncedAt(branch.getDigitalPresenceUpdatedAt())
                .build();
    }

    @Transactional
    public SyncResult syncIfStale(UUID tenantId, int radiusKm) {
        if (!properties.isConfigured()) {
            return SyncResult.builder()
                    .skipped(true)
                    .message("Google Places API key not configured.")
                    .build();
        }
        Optional<Branch> pilot = findPilotBranch(tenantId);
        if (pilot.isEmpty()) {
            return SyncResult.builder().skipped(true).message("Pilot branch not found.").build();
        }
        if (isFresh(pilot.get().getDigitalPresenceUpdatedAt())) {
            return SyncResult.builder()
                    .skipped(true)
                    .message("Using cached Google data.")
                    .syncedAt(pilot.get().getDigitalPresenceUpdatedAt())
                    .build();
        }
        return syncPilotBranch(tenantId, radiusKm, false);
    }

    private GooglePlaceSnapshot resolveOwnListing(Branch branch) {
        if (branch.getGooglePlaceId() != null && !branch.getGooglePlaceId().isBlank()) {
            GooglePlaceSnapshot existing = googlePlacesClient.getPlace(branch.getGooglePlaceId());
            if (existing != null) return enrichSnapshot(existing);
        }

        String locality = LocalSpotlightKeywords.resolveLocality(branch);
        String city = LocalSpotlightKeywords.resolveCity(branch);
        String typeTerm = LocalSpotlightKeywords.primaryListingQueryTerm(branch);
        String query = String.join(" ",
                branch.getName(),
                locality,
                typeTerm,
                city).replaceAll("\\s+", " ").trim();

        List<GooglePlaceSnapshot> candidates = googlePlacesClient.searchText(
                query,
                branch.getLatitude(),
                branch.getLongitude(),
                properties.getDefaultRadiusMeters(),
                5);

        if (candidates.isEmpty()) {
            candidates = googlePlacesClient.searchText(
                    typeTerm + " " + locality + " " + city,
                    branch.getLatitude(),
                    branch.getLongitude(),
                    properties.getDefaultRadiusMeters(),
                    5);
        }
        if (candidates.isEmpty()) {
            candidates = googlePlacesClient.searchText(
                    branch.getName() + " " + locality,
                    branch.getLatitude(),
                    branch.getLongitude(),
                    properties.getDefaultRadiusMeters(),
                    5);
        }

        return pickBestOwnMatch(branch, candidates);
    }

    private GooglePlaceSnapshot enrichSnapshot(GooglePlaceSnapshot snap) {
        if (snap == null) return null;
        return googlePlacesClient.enrichWithReviewStats(snap);
    }

    private GooglePlaceSnapshot pickBestOwnMatch(Branch branch, List<GooglePlaceSnapshot> candidates) {
        if (candidates.isEmpty()) return null;

        String branchNameNorm = normalizeName(branch.getName());
        String societyNorm = branch.getSocietyDefault() != null ? normalizeName(branch.getSocietyDefault()) : "";
        Set<String> branchTokens = nameTokens(branch.getName(), branch.getSocietyDefault(), branch.getAddress());

        GooglePlaceSnapshot best = null;
        int bestScore = 0;
        for (GooglePlaceSnapshot c : candidates) {
            int score = 0;
            String nameNorm = normalizeName(c.getName());
            if (nameNorm.contains(branchNameNorm) || branchNameNorm.contains(nameNorm)) score += 3;
            score += sharedTokenScore(branchTokens, nameTokens(c.getName(), c.getFormattedAddress()));

            if (!societyNorm.isBlank() && c.getFormattedAddress() != null
                    && normalizeName(c.getFormattedAddress()).contains(societyNorm)) {
                score += 2;
            }
            if (c.getFormattedAddress() != null && branch.getAddress() != null
                    && normalizeName(c.getFormattedAddress()).contains(normalizeName("Varthur"))) {
                score += 1;
            }
            if (c.getLatitude() != null && branch.getLatitude() != null) {
                double dist = haversineMeters(branch.getLatitude(), branch.getLongitude(), c.getLatitude(), c.getLongitude());
                // Geofence (attendance) may be at society gate while GBP pin is on storefront ~1–3 km away.
                if (dist < 800) score += 3;
                else if (dist < 3500) score += 2;
                else if (dist < 6000) score += 1;
            }
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return bestScore >= 2 ? best : null;
    }

    private static Set<String> nameTokens(String... parts) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            for (String token : part.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
                if (token.length() >= 4 && !STOP_WORDS.contains(token)) {
                    tokens.add(token);
                }
            }
        }
        return tokens;
    }

    private static int sharedTokenScore(Set<String> branchTokens, Set<String> candidateTokens) {
        int shared = 0;
        for (String token : branchTokens) {
            if (candidateTokens.contains(token)) {
                shared++;
            }
        }
        if (shared >= 2) return 4;
        if (shared == 1) return 2;
        return 0;
    }

    private static final Set<String> STOP_WORDS = Set.of(
            "salon", "spa", "bangalore", "bengaluru", "india", "karnataka", "near", "road", "main");

    private void applySnapshotToBranch(Branch branch, GooglePlaceSnapshot snap) {
        if (snap != null) {
            branch.setGooglePlaceId(snap.getPlaceId());
            branch.setGoogleMapsUrl(snap.mapsUriOrFallback());
            branch.setGoogleReviewUrl(snap.reviewUrl());
            branch.setGoogleFormattedAddress(snap.getFormattedAddress());
            branch.setGoogleRating(snap.getRating());
            branch.setGoogleReviewCount(snap.getReviewCount());
            branch.setGoogleLowRatingReviewCount(snap.getLowRatingReviewCount());
            branch.setGoogleReviewsSampleSize(snap.getReviewsSampleSize());
            branch.setGbpPhotoCount(snap.getPhotoCount());
            branch.setGbpHasPhone(snap.getPhone() != null && !snap.getPhone().isBlank());
            branch.setGbpHasWebsite(snap.getWebsiteUri() != null && !snap.getWebsiteUri().isBlank());
            branch.setGbpHasHours(snap.isHasOpeningHours());
            branch.setGbpHasBookButton(false);
            branch.setGbpVideoCount(0);
            branch.setGbpServicesListedCount(null);
        } else {
            branch.setGooglePlaceId(null);
            branch.setGoogleMapsUrl(mapsPinUrl(branch.getLatitude(), branch.getLongitude()));
            branch.setGoogleReviewUrl(null);
            branch.setGoogleRating(null);
            branch.setGoogleReviewCount(null);
            branch.setGoogleLowRatingReviewCount(null);
            branch.setGoogleReviewsSampleSize(null);
            branch.setGbpPhotoCount(null);
            branch.setGbpHasPhone(null);
            branch.setGbpHasWebsite(null);
            branch.setGbpHasHours(null);
            branch.setGoogleFormattedAddress(null);
            branch.setGoogleSearchRankData(null);
        }
    }

    private void upsertAutoDiscoveredCompetitor(UUID tenantId, UUID branchId, GooglePlaceSnapshot snap) {
        String placeId = snap.getPlaceId();
        Optional<LocalCompetitor> existing = localCompetitorRepository
                .findByTenantIdAndActiveTrueOrderByNameAsc(tenantId).stream()
                .filter(c -> placeId.equals(c.getGooglePlaceId()))
                .findFirst();

        LocalCompetitor entity = existing.orElseGet(LocalCompetitor::new);
        entity.setTenantId(tenantId);
        entity.setBranchId(branchId);
        entity.setName(snap.getName());
        entity.setCompetitorType(CompetitorType.LOCAL);
        entity.setAddress(snap.getFormattedAddress());
        entity.setGooglePlaceId(placeId);
        entity.setGoogleMapsUrl(snap.mapsUriOrFallback());
        entity.setGoogleRating(snap.getRating());
        entity.setGoogleReviewCount(snap.getReviewCount());
        entity.setGoogleLowRatingReviewCount(snap.getLowRatingReviewCount());
        entity.setGoogleReviewsSampleSize(snap.getReviewsSampleSize());
        entity.setGbpPhotoCount(snap.getPhotoCount());
        entity.setGbpVideoCount(0);
        entity.setGbpHasPhone(snap.getPhone() != null && !snap.getPhone().isBlank());
        entity.setGoogleAutoDiscovered(true);
        entity.setGoogleSyncedAt(Instant.now());
        entity.setActive(true);
        localCompetitorRepository.save(entity);
    }

    private void deactivateAutoDiscoveredForBranch(UUID tenantId, UUID branchId) {
        localCompetitorRepository.findByTenantIdAndBranchIdAndActiveTrueOrderByNameAsc(tenantId, branchId).stream()
                .filter(c -> Boolean.TRUE.equals(c.getGoogleAutoDiscovered()))
                .forEach(c -> {
                    c.setActive(false);
                    localCompetitorRepository.save(c);
                });
    }

    private void requireGeofence(Branch branch) {
        if (branch.getLatitude() == null || branch.getLongitude() == null) {
            throw new BadRequestException(
                    "Branch " + branch.getCode() + " has no geofence coordinates. Set lat/lng in Organization → Geofence.");
        }
    }

    private boolean isFresh(Instant updatedAt) {
        if (updatedAt == null) return false;
        return updatedAt.isAfter(Instant.now().minus(properties.getSyncCacheHours(), ChronoUnit.HOURS));
    }

    private String writeRankEntries(List<GoogleSearchRankEntry> entries) {
        try {
            return objectMapper.writeValueAsString(entries);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize search rank data: {}", e.getMessage());
            return null;
        }
    }

    public List<GoogleSearchRankEntry> readRankEntries(Branch branch) {
        if (branch == null || branch.getGoogleSearchRankData() == null || branch.getGoogleSearchRankData().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(branch.getGoogleSearchRankData(), new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse search rank data for branch {}: {}", branch.getCode(), e.getMessage());
            return List.of();
        }
    }

    private static String mapsPinUrl(Double lat, Double lng) {
        if (lat == null || lng == null) return null;
        return "https://www.google.com/maps/search/?api=1&query=" + lat + "," + lng;
    }

    private static String normalizeName(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    @Data
    @Builder
    public static class SyncResult {
        private boolean skipped;
        private UUID branchId;
        private String branchName;
        private boolean ownListingMatched;
        private String ownListingName;
        private String googleMapsUrl;
        private String googleFormattedAddress;
        private int rivalsSynced;
        private Map<String, Integer> searchRanks;
        private String message;
        private Instant syncedAt;
    }
}
