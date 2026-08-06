package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class LocalSpotlightSyncResponse {
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
