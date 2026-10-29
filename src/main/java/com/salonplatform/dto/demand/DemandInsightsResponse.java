package com.salonplatform.dto.demand;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class DemandInsightsResponse {
    private LocalDate startDate;
    private LocalDate endDate;
    private String timezone;
    private int periodDays;
    private List<BranchDemand> branches;
}
