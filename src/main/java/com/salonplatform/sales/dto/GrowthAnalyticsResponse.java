package com.salonplatform.sales.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class GrowthAnalyticsResponse {
    private long activeCustomers;
    private long trialCustomers;
    private long totalCustomers;
    private BigDecimal pipelineMrr;
    private BigDecimal wonMrr;
    private List<PeriodMetric> customerTrend;
    private List<PeriodMetric> mrrTrend;
}
