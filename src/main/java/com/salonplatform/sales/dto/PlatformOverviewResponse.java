package com.salonplatform.sales.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class PlatformOverviewResponse {
    /** Total tenant customers on the platform (all time). */
    private long totalCustomersAllTime;
    private long activeCustomers;
    private long trialCustomers;
    /** WON conversions with convertedAt in the selected period. */
    private long customersAcquiredInPeriod;
    /** Leads currently in FREE_TRIAL (not yet won). */
    private long freeTrialNotWon;
    /** Cumulative monthly-equivalent revenue from all WON leads. */
    private BigDecimal totalRevenueWonAllTime;
    /** Cumulative monthly-equivalent revenue from all LOST leads. */
    private BigDecimal totalRevenueLostAllTime;
    /** Metrics for leads created in the selected date range. */
    private PipelineSummaryResponse periodSummary;
    /** Per-rep performance in the selected date range. */
    private List<RepPerformanceResponse> repTrend;
}
