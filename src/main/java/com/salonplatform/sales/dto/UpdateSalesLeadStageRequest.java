package com.salonplatform.sales.dto;

import com.salonplatform.sales.domain.enums.LeadStage;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateSalesLeadStageRequest {

    @NotNull
    private LeadStage stage;

    private String notes;

    private String lostReason;
}
