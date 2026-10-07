package com.salonplatform.service;

import com.salonplatform.dto.analytics.LocalSpotlightSyncResponse;
import com.salonplatform.google.DigitalPresenceSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class LocalSpotlightSyncAsyncRunner {

    private final DigitalPresenceSyncService digitalPresenceSyncService;
    private final LocalSpotlightSyncProgressService syncProgressService;

    @Async
    public void run(UUID tenantId, int radiusKm, boolean force, boolean forceKeywords) {
        try {
            DigitalPresenceSyncService.SyncResult result =
                    digitalPresenceSyncService.syncPilotBranch(tenantId, radiusKm, force, forceKeywords);
            syncProgressService.recordLastOutcome(tenantId, toResponse(result), null);
        } catch (RuntimeException e) {
            log.warn("Local Spotlight Google sync failed for tenant {}: {}", tenantId, e.getMessage());
            syncProgressService.clear(tenantId);
            syncProgressService.recordLastOutcome(tenantId, null, e.getMessage());
        }
    }

    private static LocalSpotlightSyncResponse toResponse(DigitalPresenceSyncService.SyncResult result) {
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
}
