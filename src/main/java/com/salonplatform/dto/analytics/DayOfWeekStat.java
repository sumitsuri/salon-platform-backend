package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class DayOfWeekStat {
    private String day;
    private String dayLabel;
    private BigDecimal revenue;
    private long visits;
    private double avgRevenuePerDay;
    private double vsWeeklyAvgPct;
    private boolean slowDay;
}
