package com.salonplatform.sales.application;

import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.repository.UserRepository;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.google.GooglePlaceSnapshot;
import com.salonplatform.google.GooglePlacesClient;
import com.salonplatform.sales.config.SalesMapIngestionProperties;
import com.salonplatform.sales.domain.entity.SalesLead;
import com.salonplatform.sales.domain.entity.SalesLocality;
import com.salonplatform.sales.domain.entity.SalesMapSyncJob;
import com.salonplatform.sales.domain.entity.SalesStageHistory;
import com.salonplatform.sales.domain.enums.LeadClaimStatus;
import com.salonplatform.sales.domain.enums.MapSyncStatus;
import com.salonplatform.sales.domain.enums.LeadSource;
import com.salonplatform.sales.domain.enums.LeadStage;
import com.salonplatform.sales.domain.enums.LeadType;
import com.salonplatform.sales.domain.repository.SalesLeadRepository;
import com.salonplatform.sales.domain.repository.SalesLocalityRepository;
import com.salonplatform.sales.domain.repository.SalesMapSyncJobRepository;
import com.salonplatform.sales.domain.repository.SalesStageHistoryRepository;
import com.salonplatform.sales.dto.ClaimDiscoverSalonRequest;
import com.salonplatform.sales.dto.DiscoverSalonsRequest;
import com.salonplatform.sales.dto.DiscoverSalonsResponse;
import com.salonplatform.sales.dto.DiscoveredSalonPreview;
import com.salonplatform.sales.dto.MapIngestionSummary;
import com.salonplatform.sales.dto.SalesLeadResponse;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesLeadDiscoveryService {

    private static final List<String> NEARBY_TYPES = List.of(
            "hair_salon", "beauty_salon", "spa", "barber_shop", "nail_salon");

    private static final int TEXT_SEARCH_PAGES_PER_QUERY = 3;

    private static final List<String> TEXT_SEARCH_QUERIES = List.of(
            "hair salon near %s Bangalore",
            "beauty salon near %s Bangalore",
            "unisex salon near %s Bangalore",
            "spa near %s Bangalore",
            "barber shop near %s Bangalore",
            "nail salon near %s Bangalore",
            "nail studio near %s Bangalore",
            "salon and spa near %s Bangalore");

    private final SalesLocalityRepository localityRepository;
    private final SalesLeadRepository leadRepository;
    private final SalesStageHistoryRepository stageHistoryRepository;
    private final GooglePlacesClient googlePlacesClient;
    private final UserRepository userRepository;
    private final SalesMapIngestionProperties mapIngestionProperties;
    private final SalesLeadService salesLeadService;
    private final SalesMapSyncJobRepository syncJobRepository;
    private final ObjectProvider<SalesLeadDiscoveryService> self;

    @Transactional(readOnly = true)
    public DiscoverSalonsResponse preview(DiscoverSalonsRequest request) {
        SecurityUtils.assertSalesAccess();
        SalesLocality area = requireMappableArea(request.getLocalityId());
        int radiusKm = clampRadius(request.getRadiusKm());
        UUID currentRep = SecurityUtils.currentUserId();

        List<SalesLead> stored = leadRepository.findBySourceAndLocalityId(LeadSource.MAP_DISCOVERY, area.getId()).stream()
                .filter(lead -> leadWithinRadius(lead, area, radiusKm))
                .sorted(Comparator.comparing(
                        lead -> distanceKmForLead(lead, area),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<DiscoveredSalonPreview> previews =
                stored.stream().map(lead -> toPreviewFromLead(lead, area, currentRep)).toList();

        Instant lastSynced = stored.stream()
                .map(SalesLead::getLastMapSyncAt)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        UUID activeJobId = syncJobRepository
                .findFirstByStatusInOrderByCreatedAtDesc(EnumSet.of(MapSyncStatus.QUEUED, MapSyncStatus.RUNNING))
                .map(SalesMapSyncJob::getId)
                .orElse(null);

        return DiscoverSalonsResponse.builder()
                .areaName(area.getName())
                .radiusKm(radiusKm)
                .placesFound(previews.size())
                .imported(0)
                .skippedDuplicate(previews.size())
                .previews(previews)
                .fromCrm(true)
                .lastSyncedAt(lastSynced)
                .activeSyncJobId(activeJobId)
                .build();
    }

    /** Runs inside async worker — updates {@link SalesMapSyncJob} progress (no outer transaction so UI sees progress). */
    public void executeMapSyncJob(UUID jobId) {
        SalesMapSyncJob job = syncJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Sync job not found"));

        List<SalesLocality> areas;
        if (job.getLocalityId() != null) {
            areas = List.of(requireMappableArea(job.getLocalityId()));
        } else {
            areas = localityRepository.findByActiveTrueOrderByZoneAscNameAsc().stream()
                    .filter(l -> l.getLatitude() != null && l.getLongitude() != null)
                    .toList();
        }

        job.setTotalAreas(areas.size());
        syncJobRepository.save(job);

        int inserted = 0;
        int skipped = 0;
        int errors = 0;
        int completed = 0;

        for (SalesLocality area : areas) {
            job.setCurrentAreaName(area.getName());
            syncJobRepository.save(job);
            try {
                int[] counts = self.getObject().syncIngestAreaPool(area, job.getRadiusKm());
                inserted += counts[0];
                skipped += counts[1];
            } catch (Exception e) {
                errors++;
                log.warn("Map sync failed for {}: {}", area.getName(), e.getMessage());
            }
            completed++;
            job.setCompletedAreas(completed);
            job.setLeadsInserted(inserted);
            job.setLeadsSkippedDuplicate(skipped);
            job.setAreaErrors(errors);
            syncJobRepository.save(job);
        }
    }

    /** Background job: ingest map leads for every active Bangalore area (unassigned pool). */
    @Transactional
    public MapIngestionSummary ingestAllBangaloreAreas(int radiusKm) {
        int radius = clampRadius(radiusKm);
        int inserted = 0;
        int skipped = 0;
        int errors = 0;
        int areas = 0;
        List<SalesLocality> localities = localityRepository.findByActiveTrueOrderByZoneAscNameAsc();
        for (SalesLocality area : localities) {
            if (area.getLatitude() == null || area.getLongitude() == null) {
                continue;
            }
            areas++;
            try {
                int[] counts = ingestAreaPool(area, radius);
                inserted += counts[0];
                skipped += counts[1];
            } catch (Exception e) {
                errors++;
                log.warn("Map ingestion failed for area {}: {}", area.getName(), e.getMessage());
            }
        }
        return MapIngestionSummary.builder()
                .areasProcessed(areas)
                .leadsInserted(inserted)
                .leadsSkippedDuplicate(skipped)
                .areaErrors(errors)
                .build();
    }

    @Transactional
    public DiscoverSalonsResponse importSalons(DiscoverSalonsRequest request) {
        SecurityUtils.assertSalesAccess();
        SalesLocality area = requireMappableArea(request.getLocalityId());
        int radiusKm = clampRadius(request.getRadiusKm());
        UUID repId = resolveAssignRep(request.getAssignRepId());
        int[] counts = ingestAreaAssigned(area, radiusKm, repId);
        List<GooglePlaceSnapshot> places = fetchSalons(area, radiusKm);
        List<DiscoveredSalonPreview> previews = toPreviews(places, area);
        return DiscoverSalonsResponse.builder()
                .areaName(area.getName())
                .radiusKm(radiusKm)
                .placesFound(previews.size())
                .imported(counts[0])
                .skippedDuplicate(counts[1])
                .previews(previews)
                .build();
    }

    @Transactional
    public SalesLeadResponse claimDiscoverSalon(ClaimDiscoverSalonRequest request) {
        SecurityUtils.assertSalesAccess();
        UUID repId = SecurityUtils.currentUserId();
        String placeId = normalizePlaceId(request.getGooglePlaceId());
        if (placeId.isBlank()) {
            throw new BadRequestException("Google place id is required");
        }

        SalesLead lead = leadRepository.findByGooglePlaceId(placeId).orElseGet(() -> {
            SalesLocality area = request.getLocalityId() != null
                    ? localityRepository.findById(request.getLocalityId()).orElse(null)
                    : null;
            int radiusKm = request.getRadiusKm() != null ? clampRadius(request.getRadiusKm()) : 5;
            return createPoolLeadFromDiscoverRequest(request, placeId, area, radiusKm);
        });

        applyClaim(lead, repId);
        return salesLeadService.toResponse(leadRepository.save(lead));
    }

    @Transactional
    public SalesLeadResponse claimLead(UUID leadId) {
        SecurityUtils.assertSalesAccess();
        SalesLead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found"));
        applyClaim(lead, SecurityUtils.currentUserId());
        return salesLeadService.toResponse(leadRepository.save(lead));
    }

    private void applyClaim(SalesLead lead, UUID repId) {
        clearExpiredClaim(lead);
        if (isActiveClaimByOther(lead, repId)) {
            String holder = resolveRepName(lead.getClaimedByRepId());
            throw new BadRequestException(
                    holder != null
                            ? "Lead is claimed by " + holder + " until "
                                    + lead.getClaimExpiresAt()
                            : "Lead is claimed by another rep");
        }
        Instant expires = Instant.now().plus(mapIngestionProperties.getClaimDurationDays(), ChronoUnit.DAYS);
        lead.setClaimedByRepId(repId);
        lead.setClaimExpiresAt(expires);
        lead.setAssignedRepId(repId);
        stageHistoryRepository.save(SalesStageHistory.builder()
                .leadId(lead.getId())
                .fromStage(lead.getStage())
                .toStage(lead.getStage())
                .changedByUserId(repId)
                .notes("Lead claimed for " + mapIngestionProperties.getClaimDurationDays() + " days")
                .build());
    }

    @Transactional
    public int[] syncIngestAreaPool(SalesLocality area, int radiusKm) {
        return ingestPlaces(area, radiusKm, null);
    }

    private int[] ingestAreaPool(SalesLocality area, int radiusKm) {
        return ingestPlaces(area, radiusKm, null);
    }

    private int[] ingestAreaAssigned(SalesLocality area, int radiusKm, UUID repId) {
        return ingestPlaces(area, radiusKm, repId);
    }

    private int[] ingestPlaces(SalesLocality area, int radiusKm, UUID assignRepId) {
        List<GooglePlaceSnapshot> places = fetchSalons(area, radiusKm);
        int imported = 0;
        int skipped = 0;
        for (GooglePlaceSnapshot place : places) {
            String placeId = normalizePlaceId(place.getPlaceId());
            if (placeId.isBlank()) {
                continue;
            }
            Optional<SalesLead> existing = leadRepository.findByGooglePlaceId(placeId);
            if (existing.isPresent()) {
                applyMapSnapshot(existing.get(), place, area, radiusKm);
                leadRepository.save(existing.get());
                skipped++;
                continue;
            }
            SalesLead lead = buildLeadFromPlace(place, area, radiusKm, assignRepId);
            leadRepository.save(lead);
            if (assignRepId != null) {
                stageHistoryRepository.save(SalesStageHistory.builder()
                        .leadId(lead.getId())
                        .fromStage(null)
                        .toStage(LeadStage.NEW)
                        .changedByUserId(assignRepId)
                        .notes("Lead imported from Google Maps")
                        .build());
            }
            imported++;
        }
        return new int[] {imported, skipped};
    }

    private SalesLead buildLeadFromPlace(
            GooglePlaceSnapshot place, SalesLocality area, int radiusKm, UUID assignRepId) {
        String placeId = normalizePlaceId(place.getPlaceId());
        return SalesLead.builder()
                .businessName(place.getName() != null ? place.getName().trim() : "Salon")
                .contactName("Salon desk")
                .phone(resolvePhone(place.getPhone()))
                .leadType(LeadType.SHOP)
                .stage(LeadStage.NEW)
                .source(LeadSource.MAP_DISCOVERY)
                .localityId(area.getId())
                .localityName(area.getName())
                .address(place.getFormattedAddress())
                .city("Bangalore")
                .googlePlaceId(placeId)
                .expectedBranches(1)
                .assignedRepId(assignRepId)
                .notes(buildDiscoveryNotes(place, radiusKm))
                .mapLatitude(place.getLatitude())
                .mapLongitude(place.getLongitude())
                .mapRating(place.getRating())
                .mapReviewCount(place.getReviewCount())
                .mapPhotoRef(place.getPhotoName())
                .mapCategory(formatPrimaryType(place.getPrimaryType()))
                .mapGoogleMapsUrl(place.mapsUriOrFallback())
                .lastMapSyncAt(Instant.now())
                .build();
    }

    private void applyMapSnapshot(
            SalesLead lead, GooglePlaceSnapshot place, SalesLocality area, int radiusKm) {
        lead.setLocalityId(area.getId());
        lead.setLocalityName(area.getName());
        if (place.getName() != null && !place.getName().isBlank()) {
            lead.setBusinessName(place.getName().trim());
        }
        if (place.getFormattedAddress() != null) {
            lead.setAddress(place.getFormattedAddress());
        }
        if (place.getPhone() != null && !place.getPhone().isBlank()) {
            lead.setPhone(resolvePhone(place.getPhone()));
        }
        lead.setMapLatitude(place.getLatitude());
        lead.setMapLongitude(place.getLongitude());
        lead.setMapRating(place.getRating());
        lead.setMapReviewCount(place.getReviewCount());
        lead.setMapPhotoRef(place.getPhotoName());
        lead.setMapCategory(formatPrimaryType(place.getPrimaryType()));
        lead.setMapGoogleMapsUrl(place.mapsUriOrFallback());
        lead.setLastMapSyncAt(Instant.now());
        if (lead.getNotes() == null || lead.getNotes().isBlank()) {
            lead.setNotes(buildDiscoveryNotes(place, radiusKm));
        }
    }

    private DiscoveredSalonPreview toPreviewFromLead(SalesLead lead, SalesLocality area, UUID currentRepId) {
        clearExpiredClaim(lead);
        LeadClaimStatus claimStatus = resolveClaimStatus(lead, currentRepId);
        boolean claimable = claimStatus == LeadClaimStatus.UNCLAIMED
                || claimStatus == LeadClaimStatus.CLAIM_EXPIRED;
        Double distanceKm = distanceKmForLead(lead, area);

        return DiscoveredSalonPreview.builder()
                .googlePlaceId(lead.getGooglePlaceId())
                .businessName(lead.getBusinessName())
                .address(lead.getAddress())
                .phone(lead.getPhone())
                .rating(lead.getMapRating())
                .reviewCount(lead.getMapReviewCount())
                .googleMapsUrl(lead.getMapGoogleMapsUrl())
                .category(lead.getMapCategory())
                .photoRef(lead.getMapPhotoRef())
                .openNow(null)
                .hoursSummary(null)
                .distanceKm(distanceKm)
                .alreadyLead(true)
                .leadId(lead.getId())
                .leadStage(lead.getStage())
                .claimStatus(claimStatus)
                .claimedByRepId(lead.getClaimedByRepId())
                .claimedByRepName(resolveRepName(lead.getClaimedByRepId()))
                .claimExpiresAt(lead.getClaimExpiresAt())
                .claimable(claimable)
                .build();
    }

    private static Double distanceKmForLead(SalesLead lead, SalesLocality area) {
        if (lead.getMapLatitude() == null || lead.getMapLongitude() == null) {
            return null;
        }
        return Math.round(
                        haversineKm(
                                        area.getLatitude(),
                                        area.getLongitude(),
                                        lead.getMapLatitude(),
                                        lead.getMapLongitude())
                                * 10.0)
                / 10.0;
    }

    private static boolean leadWithinRadius(SalesLead lead, SalesLocality area, int radiusKm) {
        if (lead.getMapLatitude() == null || lead.getMapLongitude() == null) {
            return true;
        }
        return haversineKm(area.getLatitude(), area.getLongitude(), lead.getMapLatitude(), lead.getMapLongitude())
                <= radiusKm + 0.05;
    }

    private SalesLead createPoolLeadFromDiscoverRequest(
            ClaimDiscoverSalonRequest request, String placeId, SalesLocality area, int radiusKm) {
        SalesLead lead = SalesLead.builder()
                .businessName(
                        request.getBusinessName() != null && !request.getBusinessName().isBlank()
                                ? request.getBusinessName().trim()
                                : "Salon")
                .contactName("Salon desk")
                .phone(resolvePhone(request.getPhone()))
                .leadType(LeadType.SHOP)
                .stage(LeadStage.NEW)
                .source(LeadSource.MAP_DISCOVERY)
                .localityId(area != null ? area.getId() : null)
                .localityName(area != null ? area.getName() : "Bangalore")
                .address(request.getAddress())
                .city("Bangalore")
                .googlePlaceId(placeId)
                .expectedBranches(1)
                .notes("Claimed from live map search (" + radiusKm + " km)")
                .build();
        SalesLead saved = leadRepository.save(lead);
        stageHistoryRepository.save(SalesStageHistory.builder()
                .leadId(saved.getId())
                .fromStage(null)
                .toStage(LeadStage.NEW)
                .changedByUserId(SecurityUtils.currentUserId())
                .notes("Lead created from map claim")
                .build());
        return saved;
    }

    private List<GooglePlaceSnapshot> fetchSalons(SalesLocality area, int radiusKm) {
        double lat = area.getLatitude();
        double lng = area.getLongitude();
        int radiusMeters = radiusKm * 1000;
        Map<String, GooglePlaceSnapshot> byId = new LinkedHashMap<>();

        for (String type : NEARBY_TYPES) {
            mergePlaces(
                    byId,
                    googlePlacesClient.searchNearby(lat, lng, radiusMeters, 20, List.of(type)));
        }

        for (String template : TEXT_SEARCH_QUERIES) {
            String query = String.format(template, area.getName());
            mergePlaces(
                    byId,
                    googlePlacesClient.searchTextAllPages(query, lat, lng, radiusMeters, TEXT_SEARCH_PAGES_PER_QUERY));
        }

        return byId.values().stream()
                .filter(p -> withinRadiusKm(p, lat, lng, radiusKm))
                .toList();
    }

    private static boolean withinRadiusKm(GooglePlaceSnapshot place, double centerLat, double centerLng, int radiusKm) {
        if (place.getLatitude() == null || place.getLongitude() == null) {
            return true;
        }
        return haversineKm(centerLat, centerLng, place.getLatitude(), place.getLongitude()) <= radiusKm + 0.05;
    }

    private static void mergePlaces(Map<String, GooglePlaceSnapshot> byId, List<GooglePlaceSnapshot> found) {
        for (GooglePlaceSnapshot snap : found) {
            if (snap == null || snap.getPlaceId() == null) {
                continue;
            }
            String id = normalizePlaceId(snap.getPlaceId());
            byId.putIfAbsent(id, snap);
        }
    }

    private SalesLocality requireMappableArea(UUID localityId) {
        SalesLocality area = localityRepository.findById(localityId)
                .orElseThrow(() -> new ResourceNotFoundException("Area not found"));
        if (area.getLatitude() == null || area.getLongitude() == null) {
            throw new BadRequestException("This area is not configured for map discovery yet");
        }
        if (!area.isActive()) {
            throw new BadRequestException("Area is inactive");
        }
        return area;
    }

    private UUID resolveAssignRep(UUID requested) {
        if (SecurityUtils.isSalesExecutive()) {
            return SecurityUtils.currentUserId();
        }
        if (requested != null) {
            return requested;
        }
        return SecurityUtils.currentUserId();
    }

    private static int clampRadius(int radiusKm) {
        if (radiusKm < 1) {
            return 1;
        }
        return Math.min(radiusKm, 15);
    }

    private List<DiscoveredSalonPreview> toPreviews(List<GooglePlaceSnapshot> places, SalesLocality area) {
        double lat = area.getLatitude();
        double lng = area.getLongitude();
        UUID currentRep = SecurityUtils.currentUserId();

        Set<String> placeIds = places.stream()
                .map(p -> normalizePlaceId(p.getPlaceId()))
                .filter(id -> !id.isBlank())
                .collect(Collectors.toSet());
        Map<String, SalesLead> leadsByPlaceId = leadRepository.findByGooglePlaceIdIn(placeIds).stream()
                .collect(Collectors.toMap(SalesLead::getGooglePlaceId, Function.identity(), (a, b) -> a));

        return places.stream()
                .map(p -> toPreview(p, lat, lng, leadsByPlaceId.get(normalizePlaceId(p.getPlaceId())), currentRep))
                .sorted(java.util.Comparator.comparing(
                        DiscoveredSalonPreview::getDistanceKm,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();
    }

    private DiscoveredSalonPreview toPreview(
            GooglePlaceSnapshot place,
            double centerLat,
            double centerLng,
            SalesLead lead,
            UUID currentRepId) {
        Double distanceKm = null;
        if (place.getLatitude() != null && place.getLongitude() != null) {
            distanceKm = Math.round(haversineKm(centerLat, centerLng, place.getLatitude(), place.getLongitude()) * 10.0)
                    / 10.0;
        }
        if (lead != null) {
            clearExpiredClaim(lead);
        }
        LeadClaimStatus claimStatus = resolveClaimStatus(lead, currentRepId);
        boolean inCrm = lead != null;
        boolean claimable = claimStatus == LeadClaimStatus.OPEN
                || claimStatus == LeadClaimStatus.UNCLAIMED
                || claimStatus == LeadClaimStatus.CLAIM_EXPIRED;

        return DiscoveredSalonPreview.builder()
                .googlePlaceId(normalizePlaceId(place.getPlaceId()))
                .businessName(place.getName())
                .address(place.getFormattedAddress())
                .phone(place.getPhone())
                .rating(place.getRating())
                .reviewCount(place.getReviewCount())
                .googleMapsUrl(place.mapsUriOrFallback())
                .websiteUrl(place.getWebsiteUri())
                .category(formatPrimaryType(place.getPrimaryType()))
                .photoRef(place.getPhotoName())
                .photoCount(place.getPhotoCount())
                .openNow(place.getOpenNow())
                .hoursSummary(place.getHoursSummary())
                .distanceKm(distanceKm)
                .alreadyLead(inCrm)
                .leadId(lead != null ? lead.getId() : null)
                .leadStage(lead != null ? lead.getStage() : null)
                .claimStatus(claimStatus)
                .claimedByRepId(lead != null ? lead.getClaimedByRepId() : null)
                .claimedByRepName(lead != null ? resolveRepName(lead.getClaimedByRepId()) : null)
                .claimExpiresAt(lead != null ? lead.getClaimExpiresAt() : null)
                .claimable(claimable)
                .build();
    }

    private LeadClaimStatus resolveClaimStatus(SalesLead lead, UUID currentRepId) {
        if (lead == null) {
            return LeadClaimStatus.OPEN;
        }
        if (!isActiveClaim(lead)) {
            return LeadClaimStatus.UNCLAIMED;
        }
        if (Objects.equals(currentRepId, lead.getClaimedByRepId())) {
            return LeadClaimStatus.CLAIMED_BY_ME;
        }
        return LeadClaimStatus.CLAIMED_BY_OTHER;
    }

    private static boolean isActiveClaim(SalesLead lead) {
        return lead.getClaimedByRepId() != null
                && lead.getClaimExpiresAt() != null
                && lead.getClaimExpiresAt().isAfter(Instant.now());
    }

    private static boolean isActiveClaimByOther(SalesLead lead, UUID repId) {
        return isActiveClaim(lead) && !Objects.equals(repId, lead.getClaimedByRepId());
    }

    private static void clearExpiredClaim(SalesLead lead) {
        if (lead.getClaimedByRepId() != null
                && lead.getClaimExpiresAt() != null
                && !lead.getClaimExpiresAt().isAfter(Instant.now())) {
            lead.setClaimedByRepId(null);
            lead.setClaimExpiresAt(null);
        }
    }

    private String resolveRepName(UUID repId) {
        if (repId == null) {
            return null;
        }
        return userRepository.findById(repId).map(User::getName).orElse(null);
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static String formatPrimaryType(String primaryType) {
        if (primaryType == null || primaryType.isBlank()) {
            return null;
        }
        String[] parts = primaryType.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private static int countInCrm(List<DiscoveredSalonPreview> previews) {
        return (int) previews.stream().filter(DiscoveredSalonPreview::isAlreadyLead).count();
    }

    private static String normalizePlaceId(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.startsWith("places/") ? raw.substring("places/".length()) : raw;
    }

    private static String resolvePhone(String phone) {
        if (phone != null && !phone.isBlank()) {
            return phone.trim();
        }
        return "0000000000";
    }

    private static String buildDiscoveryNotes(GooglePlaceSnapshot place, int radiusKm) {
        StringBuilder sb = new StringBuilder("Imported from Google Maps (").append(radiusKm).append(" km radius).");
        if (place.getRating() != null) {
            sb.append(" Rating ").append(place.getRating());
            if (place.getReviewCount() != null) {
                sb.append(" (").append(place.getReviewCount()).append(" reviews)");
            }
            sb.append(".");
        }
        String maps = place.mapsUriOrFallback();
        if (maps != null && !maps.isBlank()) {
            sb.append(" Maps: ").append(maps);
        }
        return sb.toString();
    }
}
