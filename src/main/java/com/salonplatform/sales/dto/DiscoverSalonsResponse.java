package com.salonplatform.sales.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class DiscoverSalonsResponse {
    private String areaName;
    private int radiusKm;
    private int placesFound;
    private int imported;
    private int skippedDuplicate;
    private List<DiscoveredSalonPreview> previews;
    /** True when previews are loaded from CRM (not live Google). */
    private boolean fromCrm;
    private Instant lastSyncedAt;
    private UUID activeSyncJobId;
}
