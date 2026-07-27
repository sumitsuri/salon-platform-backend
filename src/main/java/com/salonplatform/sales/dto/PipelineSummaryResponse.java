package com.salonplatform.sales.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PipelineSummaryResponse {
    private long totalLeads;
    private long wonCount;
    private long lostCount;
    private long freeTrialCount;
    /** Leads in any stage other than WON, LOST, or FREE_TRIAL. */
    private long otherCount;
    /** Sum of monthly-equivalent revenue for won leads in range. */
    private BigDecimal wonRevenue;
    /** Sum of monthly-equivalent revenue for lost leads in range. */
    private BigDecimal lostRevenue;
}
