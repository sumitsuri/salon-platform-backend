package com.salonplatform.service;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.LocalCompetitor;
import com.salonplatform.domain.entity.Tenant;
import com.salonplatform.domain.enums.BranchStatus;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.LocalCompetitorRepository;
import com.salonplatform.domain.repository.TenantRepository;
import com.salonplatform.dto.analytics.LocalSpotlightResponse;
import com.salonplatform.dto.analytics.LocalSpotlightSyncProgressResponse;
import com.salonplatform.dto.analytics.LocalSpotlightSyncResponse;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.dto.branch.BranchResponse;
import com.salonplatform.dto.branch.UpdateBranchDigitalPresenceRequest;
import com.salonplatform.google.DigitalPresenceSyncService;
import com.salonplatform.google.GooglePlacesProperties;
import com.salonplatform.google.GoogleRankedPlace;
import com.salonplatform.google.GoogleSearchRankEntry;
import com.salonplatform.google.LocalSpotlightKeywords;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LocalSpotlightService {

    private final BranchRepository branchRepository;
    private final LocalCompetitorRepository localCompetitorRepository;
    private final BranchManagementService branchManagementService;
    private final DigitalPresenceSyncService digitalPresenceSyncService;
    private final GooglePlacesProperties googlePlacesProperties;
    private final TenantRepository tenantRepository;
    private final LocalSpotlightDailyRankService localSpotlightDailyRankService;
    private final LocalSpotlightSyncProgressService syncProgressService;

    public LocalSpotlightResponse getLocalSpotlight(
            List<UUID> branchIds, int radiusKm, boolean refresh, LocalDate rankCompareDate) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);

        String syncMessage = null;
        // Demo brands show a stored snapshot of fictional listings; never call Google for them.
        boolean demo = tenant != null && tenant.isDemo();
        // Live Google sync runs only on explicit refresh — never block the GET on background sync
        // (403 / slow Places calls were leaving the UI on "Loading digital presence…" for minutes).
        if (googlePlacesProperties.isConfigured() && !demo && refresh) {
            try {
                DigitalPresenceSyncService.SyncResult sync =
                        digitalPresenceSyncService.syncPilotBranch(tenantId, radiusKm, true);
                syncMessage = sync.getMessage();
            } catch (Exception e) {
                syncMessage = e.getMessage();
            }
        }

        List<Branch> branches = branchRepository.findByTenantId(tenantId).stream()
                .filter(b -> b.getStatus() == BranchStatus.ACTIVE)
                .filter(b -> branchIds == null || branchIds.isEmpty() || branchIds.contains(b.getId()))
                .toList();

        Optional<Branch> pilotBranch = digitalPresenceSyncService.findPilotBranch(tenantId);
        boolean pilotMode = pilotBranch.isPresent();

        List<LocalCompetitor> googleRivals = localCompetitorRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId).stream()
                .filter(c -> Boolean.TRUE.equals(c.getGoogleAutoDiscovered()))
                .filter(c -> c.getGooglePlaceId() != null && !c.getGooglePlaceId().isBlank())
                .filter(c -> branchIds == null || branchIds.isEmpty()
                        || c.getBranchId() == null
                        || branchIds.contains(c.getBranchId()))
                .toList();

        Map<UUID, List<LocalCompetitor>> rivalsByBranch = new HashMap<>();
        for (LocalCompetitor rival : googleRivals) {
            UUID key = rival.getBranchId() != null ? rival.getBranchId() : UUID.fromString("00000000-0000-0000-0000-000000000000");
            rivalsByBranch.computeIfAbsent(key, k -> new ArrayList<>()).add(rival);
        }

        Map<UUID, String> branchNames = new HashMap<>();
        for (Branch b : branches) {
            branchNames.put(b.getId(), b.getName());
        }

        List<LocalSpotlightResponse.BranchRow> branchRows = new ArrayList<>();
        List<LocalSpotlightResponse.SearchRankRow> searchRanks = new ArrayList<>();
        boolean keywordRanksNeedRefresh = false;
        int keywordRanksExpectedTotal = 0;
        int keywordRanksStoredTotal = 0;
        int notInTop3 = 0;
        int ratingBelow = 0;
        int incompleteGbp = 0;
        int missingPhone = 0;
        int linked = 0;
        int scoreSum = 0;
        int scoredBranches = 0;

        for (Branch branch : branches) {
            boolean isPilot = tenant != null && (demo || digitalPresenceSyncService.isPilotBranch(tenant, branch));
            boolean googleSynced = isPilot && branch.getDigitalPresenceUpdatedAt() != null
                    && notBlank(branch.getGooglePlaceId());
            List<LocalCompetitor> branchRivals = googleRivals.stream()
                    .filter(r -> r.getBranchId() == null || r.getBranchId().equals(branch.getId()))
                    .toList();

            Branch effective = googleSynced ? branch : maskUnsyncedBranch(branch);

            int completeness = computeGbpCompletenessPercent(effective);
            int lvs = computeLocalVisibilityScore(effective, branchRivals, completeness);
            boolean listingLinked = isListingLinked(effective);
            if (listingLinked) linked++;
            if (googleSynced) {
                scoreSum += lvs;
                scoredBranches++;
            }

            boolean inTop3 = effective.getEstimatedSearchRank() != null && effective.getEstimatedSearchRank() <= 3;
            if (listingLinked && !inTop3) notInTop3++;
            if (listingLinked && isRatingBelowRivals(effective, branchRivals)) ratingBelow++;
            if (listingLinked && completeness < 70) incompleteGbp++;
            if (listingLinked && !Boolean.TRUE.equals(effective.getGbpHasPhone())) missingPhone++;

            branchRows.add(buildBranchRow(effective, lvs, completeness, listingLinked, branchRivals.size(), isPilot, googleSynced));

            if (googleSynced) {
                SearchRankBuildResult built = buildSearchRanks(effective, rankCompareDate);
                searchRanks.addAll(built.rows());
                keywordRanksExpectedTotal += built.expectedCount();
                keywordRanksStoredTotal += built.storedCount();
                if (built.needsRefresh()) {
                    keywordRanksNeedRefresh = true;
                }
            }
        }

        searchRanks = dedupeSearchRankRows(searchRanks);
        searchRanks.sort(Comparator.comparingInt(LocalSpotlightService::searchRankRowSortKey));

        branchRows.sort(Comparator.comparingInt(LocalSpotlightResponse.BranchRow::getLocalVisibilityScore).reversed());

        int avgScore = scoredBranches == 0 ? 0 : scoreSum / scoredBranches;
        Instant lastRefresh = branches.stream()
                .map(Branch::getDigitalPresenceUpdatedAt)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        List<LocalSpotlightResponse.RivalRow> rivalRows = googleRivals.stream()
                .map(c -> toRivalRow(c, branchNames.get(c.getBranchId())))
                .sorted(Comparator.comparingInt(this::rivalThreatScore).reversed())
                .toList();

        List<LocalSpotlightResponse.PlaybookItem> playbook = new ArrayList<>();
        for (LocalSpotlightResponse.BranchRow row : branchRows) {
            if (!row.isPilotBranch()) {
                continue;
            }
            Branch branch = branches.stream().filter(b -> b.getId().equals(row.getBranchId())).findFirst().orElse(null);
            if (branch == null) {
                continue;
            }
            List<LocalCompetitor> branchRivals = rivalsByBranch.getOrDefault(
                    branch.getId(),
                    rivalsByBranch.values().stream().flatMap(Collection::stream).toList());
            List<LocalSpotlightResponse.SearchRankRow> branchRanks = searchRanks.stream()
                    .filter(r -> r.getBranchId().equals(row.getBranchId()))
                    .toList();
            playbook.addAll(LocalSpotlightPlaybookBuilder.build(branch, row, branchRivals, branchRanks));
        }

        String dataSourceNote = demo
                ? "Google Business Profile snapshot · ~" + radiusKm + " km radius"
                : googlePlacesProperties.isConfigured()
                ? "Live Google Places data · pilot branch "
                        + googlePlacesProperties.getPilotBranchCode()
                        + " · ~"
                        + radiusKm
                        + " km radius"
                : "Configure GOOGLE_PLACES_API_KEY to sync live Google Business Profile data.";

        return LocalSpotlightResponse.builder()
                .localVisibilityScore(avgScore)
                .scoreLabel(scoreLabel(avgScore, linked, Math.max(scoredBranches, 1)))
                .branchesLinked(linked)
                .branchesTotal(branches.size())
                .notInTop3Count(notInTop3)
                .ratingBelowRivalsCount(ratingBelow)
                .incompleteGbpCount(incompleteGbp)
                .missingPhoneCount(missingPhone)
                .dataSourceNote(dataSourceNote)
                .lastRefreshedAt(lastRefresh)
                .googleApiConfigured(googlePlacesProperties.isConfigured())
                .pilotMode(pilotMode)
                .pilotBranchCode(googlePlacesProperties.getPilotBranchCode())
                .pilotBranchName(pilotBranch.map(Branch::getName).orElse(null))
                .syncStatusMessage(syncMessage)
                .branches(branchRows)
                .rivals(rivalRows)
                .searchRanks(searchRanks)
                .rankCompareDate(rankCompareDate)
                .keywordRanksNeedRefresh(keywordRanksNeedRefresh)
                .keywordRanksExpectedCount(keywordRanksExpectedTotal)
                .keywordRanksStoredCount(keywordRanksStoredTotal)
                .playbook(playbook)
                .build();
    }

    private record SearchRankBuildResult(
            List<LocalSpotlightResponse.SearchRankRow> rows, boolean needsRefresh, int expectedCount, int storedCount) {}

    private static List<LocalSpotlightResponse.SearchRankRow> dedupeSearchRankRows(
            List<LocalSpotlightResponse.SearchRankRow> rows) {
        Map<String, LocalSpotlightResponse.SearchRankRow> unique = new LinkedHashMap<>();
        for (LocalSpotlightResponse.SearchRankRow row : rows) {
            if (row.getKeyword() == null || row.getBranchId() == null) {
                continue;
            }
            String key = row.getBranchId() + "|" + row.getKeyword().trim().toLowerCase(Locale.ROOT);
            unique.putIfAbsent(key, row);
        }
        return new ArrayList<>(unique.values());
    }

    private static int searchRankRowSortKey(LocalSpotlightResponse.SearchRankRow row) {
        if (row.getYourRank() != null || row.isYourRankBeyondTop20()) {
            return 0;
        }
        if ("Refresh from Google".equals(row.getYourRankLabel())) {
            return 2;
        }
        return 1;
    }

    public com.salonplatform.dto.analytics.LocalSpotlightRankHistoryResponse keywordRankHistory(
            UUID branchId, LocalDate from, LocalDate to) {
        SecurityUtils.assertBrandAdminOrAbove();
        return localSpotlightDailyRankService.rankHistory(branchId, from, to);
    }

    public LocalSpotlightSyncProgressResponse syncProgress() {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        return syncProgressService.snapshot(tenantId).orElseGet(() -> LocalSpotlightSyncProgressResponse.builder()
                .active(false)
                .phase(LocalSpotlightSyncProgressService.Phase.IDLE.name())
                .percent(0)
                .build());
    }

    public LocalSpotlightSyncResponse syncFromGoogle(int radiusKm, boolean force, boolean forceKeywords) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        if (syncProgressService.isActive(tenantId)) {
            throw new BadRequestException(
                    "Google sync is already running for this brand. Wait for it to finish before refreshing again.");
        }
        if (tenantRepository.findById(tenantId).map(Tenant::isDemo).orElse(false)) {
            return LocalSpotlightSyncResponse.builder()
                    .skipped(true)
                    .message("Demo brand — showing a stored Google snapshot")
                    .build();
        }
        DigitalPresenceSyncService.SyncResult result;
        try {
            result = digitalPresenceSyncService.syncPilotBranch(tenantId, radiusKm, force, forceKeywords);
        } catch (RuntimeException e) {
            syncProgressService.clear(tenantId);
            throw e;
        }
        return LocalSpotlightSyncResponse.builder()
                .skipped(result.isSkipped())
                .branchId(result.getBranchId())
                .branchName(result.getBranchName())
                .ownListingMatched(result.isOwnListingMatched())
                .ownListingName(result.getOwnListingName())
                .googleMapsUrl(result.getGoogleMapsUrl())
                .googleFormattedAddress(result.getGoogleFormattedAddress())
                .rivalsSynced(result.getRivalsSynced())
                .searchRanks(result.getSearchRanks())
                .message(result.getMessage())
                .syncedAt(result.getSyncedAt())
                .build();
    }

    @Transactional
    public BranchResponse updateDigitalPresence(UUID branchId, UpdateBranchDigitalPresenceRequest request) {
        return branchManagementService.updateDigitalPresence(branchId, request);
    }

    private Branch maskUnsyncedBranch(Branch branch) {
        Branch masked = new Branch();
        masked.setId(branch.getId());
        masked.setName(branch.getName());
        masked.setCode(branch.getCode());
        masked.setAddress(branch.getAddress());
        masked.setSocietyDefault(branch.getSocietyDefault());
        masked.setLatitude(branch.getLatitude());
        masked.setLongitude(branch.getLongitude());
        masked.setDigitalPresenceUpdatedAt(null);
        return masked;
    }

    private LocalSpotlightResponse.BranchRow buildBranchRow(
            Branch branch, int lvs, int completeness, boolean listingLinked, int rivalCount,
            boolean isPilot, boolean googleSynced) {
        Integer rank = branch.getEstimatedSearchRank();
        return LocalSpotlightResponse.BranchRow.builder()
                .branchId(branch.getId())
                .branchName(branch.getName())
                .branchCode(branch.getCode())
                .localityLabel(localityLabel(branch))
                .businessType(LocalSpotlightKeywords.effectiveType(branch).name())
                .localVisibilityScore(lvs)
                .scoreLabel(scoreLabel(lvs, listingLinked ? 1 : 0, 1))
                .estimatedSearchRank(rank)
                .inTop3(rank != null && rank <= 3)
                .googleRating(branch.getGoogleRating())
                .googleReviewCount(branch.getGoogleReviewCount())
                .googleLowRatingReviewCount(branch.getGoogleLowRatingReviewCount())
                .googleReviewsSampleSize(branch.getGoogleReviewsSampleSize())
                .gbpCompletenessPercent(completeness)
                .listingLinked(listingLinked)
                .googleSynced(googleSynced)
                .pilotBranch(isPilot)
                .gbpHasPhone(Boolean.TRUE.equals(branch.getGbpHasPhone()))
                .gbpHasWebsite(Boolean.TRUE.equals(branch.getGbpHasWebsite()))
                .gbpHasHours(Boolean.TRUE.equals(branch.getGbpHasHours()))
                .gbpHasBookButton(Boolean.TRUE.equals(branch.getGbpHasBookButton()))
                .gbpPhotoCount(branch.getGbpPhotoCount())
                .gbpVideoCount(branch.getGbpVideoCount())
                .gbpServicesListedCount(branch.getGbpServicesListedCount())
                .googlePlaceId(branch.getGooglePlaceId())
                .googleMapsUrl(branch.getGoogleMapsUrl())
                .googleReviewUrl(branch.getGoogleReviewUrl())
                .googleReviewAutoPublish(branch.getGoogleReviewAutoPublish())
                .googleFormattedAddress(branch.getGoogleFormattedAddress())
                .latitude(branch.getLatitude())
                .longitude(branch.getLongitude())
                .digitalPresenceUpdatedAt(branch.getDigitalPresenceUpdatedAt())
                .trackedRivalCount(rivalCount)
                .build();
    }

    private LocalSpotlightResponse.RivalRow toRivalRow(LocalCompetitor c, String branchName) {
        return LocalSpotlightResponse.RivalRow.builder()
                .id(c.getId())
                .name(c.getName())
                .branchId(c.getBranchId())
                .branchName(branchName)
                .googleRating(c.getGoogleRating())
                .googleReviewCount(c.getGoogleReviewCount())
                .googleLowRatingReviewCount(c.getGoogleLowRatingReviewCount())
                .googleReviewsSampleSize(c.getGoogleReviewsSampleSize())
                .gbpPhotoCount(c.getGbpPhotoCount())
                .gbpVideoCount(c.getGbpVideoCount())
                .gbpHasPhone(c.getGbpHasPhone())
                .estimatedSearchRank(c.getEstimatedSearchRank())
                .address(c.getAddress())
                .googlePlaceId(c.getGooglePlaceId())
                .googleMapsUrl(c.getGoogleMapsUrl())
                .googleAutoDiscovered(Boolean.TRUE.equals(c.getGoogleAutoDiscovered()))
                .build();
    }

    private SearchRankBuildResult buildSearchRanks(Branch branch, LocalDate rankCompareDate) {
        List<String> expectedKeywords = LocalSpotlightKeywords.searchKeywords(branch);
        if (expectedKeywords.isEmpty()) {
            return new SearchRankBuildResult(List.of(), false, 0, 0);
        }

        List<GoogleSearchRankEntry> stored = digitalPresenceSyncService.readRankEntries(branch);
        List<String> storedKeywords = stored.stream()
                .map(GoogleSearchRankEntry::getKeyword)
                .filter(k -> k != null && !k.isBlank())
                .toList();
        boolean storedMatchesTemplate =
                !stored.isEmpty() && LocalSpotlightKeywords.rankKeywordsMatchStored(storedKeywords, branch);

        LocalDate today = localSpotlightDailyRankService.today();
        List<LocalSpotlightResponse.SearchRankRow> rows = new ArrayList<>();
        boolean needsRefresh = false;
        int storedCount = 0;

        for (String keyword : expectedKeywords) {
            GoogleSearchRankEntry entry = resolveKeywordRankEntry(
                    branch, keyword, stored, storedMatchesTemplate, today);
            if (entry == null) {
                needsRefresh = true;
                rows.add(enrichCompare(branch, buildPendingKeywordRow(branch, keyword), rankCompareDate));
            } else {
                storedCount++;
                rows.add(enrichCompare(branch, rowFromRankEntry(branch, entry), rankCompareDate));
            }
        }
        return new SearchRankBuildResult(rows, needsRefresh, expectedKeywords.size(), storedCount);
    }

    private GoogleSearchRankEntry resolveKeywordRankEntry(
            Branch branch,
            String keyword,
            List<GoogleSearchRankEntry> stored,
            boolean storedMatchesTemplate,
            LocalDate today) {
        if (storedMatchesTemplate) {
            GoogleSearchRankEntry fromBranch = findStoredEntry(stored, keyword);
            if (fromBranch != null) {
                return fromBranch;
            }
        } else {
            GoogleSearchRankEntry legacy = findStoredEntry(stored, keyword);
            if (legacy != null) {
                return legacy;
            }
        }
        return localSpotlightDailyRankService
                .findSnapshot(branch.getId(), keyword, today)
                .flatMap(localSpotlightDailyRankService::toRankEntry)
                .orElse(null);
    }

    private static GoogleSearchRankEntry findStoredEntry(List<GoogleSearchRankEntry> stored, String keyword) {
        return stored.stream()
                .filter(e -> e.getKeyword() != null && e.getKeyword().equalsIgnoreCase(keyword))
                .findFirst()
                .orElse(null);
    }

    private LocalSpotlightResponse.SearchRankRow rowFromRankEntry(Branch branch, GoogleSearchRankEntry entry) {
        List<LocalSpotlightResponse.TopThreeRival> topThree = mapTopThreeRivals(entry);
        return LocalSpotlightResponse.SearchRankRow.builder()
                .branchId(branch.getId())
                .branchName(branch.getName())
                .keyword(entry.getKeyword())
                .yourRank(entry.getYourRank())
                .yourRankBeyondTop20(Boolean.TRUE.equals(entry.getYourRankBeyondTop20()))
                .yourRankLabel(buildYourRankLabel(entry))
                .inTop3(entry.getYourRank() != null && entry.getYourRank() <= 3)
                .topThreeSummary(buildTopThreeSummary(topThree, entry))
                .topThreeRivals(topThree)
                .build();
    }

    private LocalSpotlightResponse.SearchRankRow buildPendingKeywordRow(Branch branch, String keyword) {
        return LocalSpotlightResponse.SearchRankRow.builder()
                .branchId(branch.getId())
                .branchName(branch.getName())
                .keyword(keyword)
                .yourRank(null)
                .yourRankBeyondTop20(false)
                .yourRankLabel("Refresh from Google")
                .inTop3(false)
                .topThreeSummary("Per-keyword ranks load after Refresh from Google")
                .topThreeRivals(List.of())
                .build();
    }

    private LocalSpotlightResponse.SearchRankRow enrichCompare(
            Branch branch, LocalSpotlightResponse.SearchRankRow row, LocalDate compareDate) {
        if (compareDate == null) {
            return row;
        }
        var snapshot = localSpotlightDailyRankService.findSnapshot(
                branch.getId(), row.getKeyword(), compareDate);
        if (snapshot.isEmpty()) {
            return row;
        }
        var past = snapshot.get();
        Integer compareRank = past.getYourRank();
        boolean compareBeyond = past.isBeyondTop20();
        Integer rankChange = computeRankChange(compareRank, compareBeyond, row.getYourRank(), row.isYourRankBeyondTop20());
        row.setCompareRank(compareRank);
        row.setCompareBeyondTop20(compareBeyond);
        row.setRankChange(rankChange);
        return row;
    }

    private static Integer computeRankChange(
            Integer compareRank, boolean compareBeyond, Integer currentRank, boolean currentBeyond) {
        if (currentRank == null && currentBeyond && compareRank == null && compareBeyond) {
            return 0;
        }
        int baseline = compareRank != null ? compareRank : (compareBeyond ? 21 : 0);
        if (baseline <= 0) {
            return null;
        }
        if (currentRank != null) {
            return baseline - currentRank;
        }
        if (currentBeyond) {
            return baseline - 21;
        }
        return null;
    }

    private boolean isListingLinked(Branch branch) {
        return notBlank(branch.getGooglePlaceId()) && notBlank(branch.getGoogleMapsUrl());
    }

    private boolean isRatingBelowRivals(Branch branch, List<LocalCompetitor> rivals) {
        if (branch.getGoogleRating() == null || rivals.isEmpty()) return false;
        double rivalAvg = rivals.stream()
                .map(LocalCompetitor::getGoogleRating)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(branch.getGoogleRating());
        return branch.getGoogleRating() < rivalAvg - 0.1;
    }

    private int computeGbpCompletenessPercent(Branch branch) {
        if (!isListingLinked(branch)) return 0;
        int total = 9;
        int score = 0;
        if (Boolean.TRUE.equals(branch.getGbpHasPhone())) score++;
        if (Boolean.TRUE.equals(branch.getGbpHasWebsite())) score++;
        if (Boolean.TRUE.equals(branch.getGbpHasHours())) score++;
        if (Boolean.TRUE.equals(branch.getGbpHasBookButton())) score++;
        if (intAtLeast(branch.getGbpPhotoCount(), 10)) score++;
        else if (intAtLeast(branch.getGbpPhotoCount(), 5)) score++;
        if (intAtLeast(branch.getGbpVideoCount(), 1)) score++;
        if (intAtLeast(branch.getGbpServicesListedCount(), 5)) score++;
        if (notBlank(branch.getGoogleReviewUrl())) score++;
        return (int) Math.round(score * 100.0 / total);
    }

    private int computeLocalVisibilityScore(Branch branch, List<LocalCompetitor> rivals, int completeness) {
        if (!isListingLinked(branch)) return 0;

        int searchScore = 0;
        if (branch.getEstimatedSearchRank() != null) {
            int rank = branch.getEstimatedSearchRank();
            if (rank <= 3) searchScore = 100;
            else if (rank <= 5) searchScore = 65;
            else if (rank <= 10) searchScore = 35;
            else searchScore = 15;
        }

        int reputationScore = 50;
        if (branch.getGoogleRating() != null) {
            double rating = branch.getGoogleRating();
            reputationScore = (int) Math.min(100, Math.round(rating / 5.0 * 100));
            if (branch.getGoogleReviewCount() != null && branch.getGoogleReviewCount() < 30) {
                reputationScore = (int) (reputationScore * 0.75);
            }
        }

        int freshnessScore = branch.getDigitalPresenceUpdatedAt() != null
                && branch.getDigitalPresenceUpdatedAt().isAfter(Instant.now().minusSeconds(30L * 24 * 3600))
                ? 100 : 60;

        return (int) Math.round(searchScore * 0.30 + reputationScore * 0.25 + completeness * 0.25 + freshnessScore * 0.20);
    }

    private String scoreLabel(int score, int linked, int total) {
        if (linked == 0 && total > 0) return "NOT_LINKED";
        if (score >= 80) return "EXCELLENT";
        if (score >= 60) return "GOOD";
        if (score >= 40) return "NEEDS_ATTENTION";
        return "CRITICAL";
    }

    private String localityLabel(Branch branch) {
        return LocalSpotlightKeywords.resolveLocality(branch);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static boolean intAtLeast(Integer value, int min) {
        return value != null && value >= min;
    }

    private List<LocalSpotlightResponse.TopThreeRival> mapTopThreeRivals(GoogleSearchRankEntry entry) {
        if (entry.getTopThreePlaces() != null && !entry.getTopThreePlaces().isEmpty()) {
            return entry.getTopThreePlaces().stream()
                    .map(this::toTopThreeRival)
                    .toList();
        }
        if (entry.getTopThree() == null || entry.getTopThree().isEmpty()) {
            return List.of();
        }
        List<LocalSpotlightResponse.TopThreeRival> legacy = new ArrayList<>();
        for (int i = 0; i < entry.getTopThree().size(); i++) {
            legacy.add(LocalSpotlightResponse.TopThreeRival.builder()
                    .rank(i + 1)
                    .name(entry.getTopThree().get(i))
                    .build());
        }
        return legacy;
    }

    private LocalSpotlightResponse.TopThreeRival toTopThreeRival(GoogleRankedPlace place) {
        return LocalSpotlightResponse.TopThreeRival.builder()
                .rank(place.getRank())
                .name(place.getName())
                .googlePlaceId(place.getGooglePlaceId())
                .googleMapsUrl(place.getGoogleMapsUrl())
                .build();
    }

    private String buildYourRankLabel(GoogleSearchRankEntry entry) {
        if (entry.getYourRank() != null) {
            return "#" + entry.getYourRank();
        }
        if (Boolean.TRUE.equals(entry.getYourRankBeyondTop20())) {
            return "Not in top 20";
        }
        return "—";
    }

    private String buildTopThreeSummary(
            List<LocalSpotlightResponse.TopThreeRival> topThree, GoogleSearchRankEntry entry) {
        if (!topThree.isEmpty()) {
            return topThree.stream()
                    .map(r -> r.getRank() + "." + r.getName())
                    .reduce((a, b) -> a + " · " + b)
                    .orElse("");
        }
        if (entry.getTopThree() != null && !entry.getTopThree().isEmpty()) {
            return String.join(" · ", entry.getTopThree());
        }
        return "Not in top 20 for this keyword";
    }

    /** Higher score = stronger local competitor (rating, volume, profile depth; penalize sub-4★ reviews). */
    private int rivalThreatScore(LocalSpotlightResponse.RivalRow rival) {
        double rating = rival.getGoogleRating() != null ? rival.getGoogleRating() : 0;
        int reviews = rival.getGoogleReviewCount() != null ? rival.getGoogleReviewCount() : 0;
        int lowRatings = rival.getGoogleLowRatingReviewCount() != null ? rival.getGoogleLowRatingReviewCount() : 0;
        int photos = rival.getGbpPhotoCount() != null ? rival.getGbpPhotoCount() : 0;
        return (int) (rating * 1000 + Math.min(reviews, 9999) + photos * 5 - lowRatings * 75);
    }
}
