package com.salonplatform.sales.dto;

import com.salonplatform.sales.domain.enums.MapSyncStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class MapSyncJobResponse {
    private UUID id;
    private MapSyncStatus status;
    private boolean allAreas;
    private UUID localityId;
    private String localityName;
    private int radiusKm;
    private int totalAreas;
    private int completedAreas;
    private String currentAreaName;
    private int leadsInserted;
    private int leadsSkippedDuplicate;
    private int areaErrors;
    private String errorMessage;
    private Instant startedAt;
    private Instant finishedAt;
    private int progressPercent;
}
