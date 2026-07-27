package com.salonplatform.sales.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class UpsertSalesTargetRequest {
    @NotNull
    private UUID repId;
    @NotNull
    private LocalDate weekStartDate;
    private int targetLeads;
    private int targetVisits;
    private int targetPitches;
    private int targetTrials;
    private int targetConversions;
}
