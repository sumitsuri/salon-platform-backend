package com.salonplatform.sales.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class RepPerformanceResponse {
    private UUID repId;
    private String repName;
    private int leadsAdded;
    private int visits;
    private int pitches;
    private int trials;
    private int conversions;
    private int lost;
    private BigDecimal revenueWon;
    private BigDecimal incentiveEarned;
    private double targetAchievementPercent;
    private boolean underperforming;
}
