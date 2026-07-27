package com.salonplatform.sales.dto;

import com.salonplatform.sales.domain.enums.LeadStage;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class SalesStageHistoryResponse {
    private UUID id;
    private LeadStage fromStage;
    private LeadStage toStage;
    private UUID changedByUserId;
    private String changedByName;
    private String notes;
    private Instant createdAt;
}
