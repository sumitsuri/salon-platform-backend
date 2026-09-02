package com.salonplatform.dto.campaign;

import com.salonplatform.domain.enums.CampaignRunStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CampaignRunResponse {
    private UUID id;
    private UUID campaignId;
    private CampaignRunStatus status;
    private Integer recipientCount;
    private Integer sentCount;
    private Integer failedCount;
    private Integer skippedCount;
    private Instant startedAt;
    private Instant completedAt;
}
