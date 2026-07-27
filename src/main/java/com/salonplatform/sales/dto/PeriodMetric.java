package com.salonplatform.sales.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PeriodMetric {
    private String period;
    private long value;
    private BigDecimal mrr;
    private Double changePercent;
}
