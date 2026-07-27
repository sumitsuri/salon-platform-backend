package com.salonplatform.sales.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class SalesTargetResponse {
    private UUID id;
    private UUID repId;
    private String repName;
    private LocalDate weekStartDate;
    private int targetLeads;
    private int targetVisits;
    private int targetPitches;
    private int targetTrials;
    private int targetConversions;
    private int actualLeads;
    private int actualVisits;
    private int actualPitches;
    private int actualTrials;
    private int actualConversions;
}
