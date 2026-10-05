package com.salonplatform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.LocalSpotlightKeywordRankDaily;
import com.salonplatform.domain.enums.BranchStatus;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.LocalSpotlightKeywordRankDailyRepository;
import com.salonplatform.dto.analytics.LocalSpotlightRankHistoryResponse;
import com.salonplatform.google.GooglePlacesClient;
import com.salonplatform.google.GoogleRankedPlace;
import com.salonplatform.google.GoogleSearchRankEntry;
import com.salonplatform.google.LocalSpotlightKeywords;
import com.salonplatform.google.LocalSpotlightSerpCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocalSpotlightDailyRankService {

    private final LocalSpotlightSerpCacheService serpCacheService;
    private final LocalSpotlightKeywordRankDailyRepository dailyRepository;
    private final BranchRepository branchRepository;
    private final ObjectMapper objectMapper;

    public LocalDate today() {
        return LocalDate.now(LocalSpotlightSerpCacheService.SNAPSHOT_ZONE);
    }

    /**
     * Fetches or reuses pin-code SERPs, records per-branch ranks for today, and returns entries
     * for the branch snapshot JSON field.
     */
    @Transactional
    public List<GoogleSearchRankEntry> syncAndRecordDailyRanks(
            UUID tenantId, Branch branch, int radiusKm, boolean forceRefresh) {
        String pin = LocalSpotlightKeywords.resolvePinCode(branch);
        if (pin.isBlank()) {
            log.warn("Branch {} has no PIN in address — skipping keyword rank sync", branch.getCode());
            return List.of();
        }
        int radiusM = radiusKm > 0 ? radiusKm * 1000 : 2000;
        LocalDate snapshotDate = today();
        List<String> keywords = LocalSpotlightKeywords.searchKeywords(branch);
        List<GoogleSearchRankEntry> entries = new ArrayList<>();
        String ownPlaceId = branch.getGooglePlaceId();

        for (String keyword : keywords) {
            LocalSpotlightSerpCacheService.CachedSerp serp =
                    serpCacheService.resolveSerp(branch, keyword, radiusM, snapshotDate, forceRefresh);
            if (LocalSpotlightKeywords.isNearMeKeyword(keyword)) {
                GooglePlacesClient.TextSearchInsight insight =
                        serpCacheService.insightForBranch(serp, ownPlaceId);
                entries.add(toRankEntry(keyword, insight));
                persistDailyRow(tenantId, branch, pin, keyword, snapshotDate, insight);
            } else {
                recordPinKeywordRanksForAllBranches(pin, keyword, serp, snapshotDate);
                GooglePlacesClient.TextSearchInsight insight =
                        serpCacheService.insightForBranch(serp, ownPlaceId);
                entries.add(toRankEntry(keyword, insight));
            }
        }
        return entries;
    }

    /**
     * One SERP per PIN + keyword per day — derive each onboarded branch's rank from the shared result set.
     */
    private void recordPinKeywordRanksForAllBranches(
            String pin,
            String keyword,
            LocalSpotlightSerpCacheService.CachedSerp serp,
            LocalDate snapshotDate) {
        List<Branch> peers =
                branchRepository.findByStatusAndAddressContaining(BranchStatus.ACTIVE, pin);
        for (Branch peer : peers) {
            if (!pin.equals(LocalSpotlightKeywords.resolvePinCode(peer))) {
                continue;
            }
            GooglePlacesClient.TextSearchInsight insight =
                    serpCacheService.insightForBranch(serp, peer.getGooglePlaceId());
            persistDailyRow(peer.getTenantId(), peer, pin, keyword, snapshotDate, insight);
        }
    }

    private static GoogleSearchRankEntry toRankEntry(String keyword, GooglePlacesClient.TextSearchInsight insight) {
        return GoogleSearchRankEntry.builder()
                .keyword(keyword)
                .yourRank(insight.rank() > 0 ? insight.rank() : null)
                .yourRankBeyondTop20(insight.rank() <= 0)
                .topThreePlaces(insight.topPlaces())
                .build();
    }

    private void persistDailyRow(
            UUID tenantId,
            Branch branch,
            String pin,
            String keyword,
            LocalDate snapshotDate,
            GooglePlacesClient.TextSearchInsight insight) {
        LocalSpotlightKeywordRankDaily row = dailyRepository
                .findByBranchIdAndKeywordAndSnapshotDate(branch.getId(), keyword, snapshotDate)
                .orElseGet(() -> LocalSpotlightKeywordRankDaily.builder()
                        .tenantId(tenantId)
                        .branchId(branch.getId())
                        .pinCode(pin)
                        .keyword(keyword)
                        .snapshotDate(snapshotDate)
                        .build());
        row.setYourRank(insight.rank() > 0 ? insight.rank() : null);
        row.setBeyondTop20(insight.rank() <= 0);
        row.setTopThreeJson(writeTopThree(insight.topPlaces()));
        row.setRecordedAt(Instant.now());
        dailyRepository.save(row);
    }

    public LocalSpotlightRankHistoryResponse rankHistory(UUID branchId, LocalDate from, LocalDate to) {
        List<LocalSpotlightKeywordRankDaily> rows =
                dailyRepository.findByBranchIdAndSnapshotDateBetweenOrderByKeywordAscSnapshotDateAsc(
                        branchId, from, to);
        List<LocalSpotlightRankHistoryResponse.DailyPoint> points = rows.stream()
                .map(r -> LocalSpotlightRankHistoryResponse.DailyPoint.builder()
                        .date(r.getSnapshotDate())
                        .keyword(r.getKeyword())
                        .pinCode(r.getPinCode())
                        .yourRank(r.getYourRank())
                        .beyondTop20(r.isBeyondTop20())
                        .build())
                .toList();
        return LocalSpotlightRankHistoryResponse.builder()
                .branchId(branchId)
                .from(from)
                .to(to)
                .points(points)
                .build();
    }

    public Integer rankOnDate(UUID branchId, String keyword, LocalDate date) {
        return dailyRepository.findByBranchIdAndKeywordAndSnapshotDate(branchId, keyword, date)
                .map(LocalSpotlightKeywordRankDaily::getYourRank)
                .orElse(null);
    }

    public Optional<LocalSpotlightKeywordRankDaily> findSnapshot(UUID branchId, String keyword, LocalDate date) {
        return dailyRepository.findByBranchIdAndKeywordAndSnapshotDate(branchId, keyword, date);
    }

    private String writeTopThree(List<GoogleRankedPlace> topPlaces) {
        if (topPlaces == null || topPlaces.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(topPlaces);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
