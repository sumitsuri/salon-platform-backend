package com.salonplatform.dto.branch;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class BranchTargetPerformanceItem {
    private UUID branchId;
    private String branchName;
    private BigDecimal monthlySalesTarget;
    private BigDecimal actualSales;
    private BigDecimal achievementPercent;
    private boolean meetingTarget;
    private boolean onTrack;
    /** Prorated target through the as-of date in the month */
    private BigDecimal expectedSalesSoFar;
    /** actualSales − expectedSalesSoFar (positive = ahead of pace) */
    private BigDecimal gapVsExpected;
    private BigDecimal dailyAverageActual;
    /** Monthly target ÷ days in month */
    private BigDecimal dailyAverageExpected;
    private int daysElapsed;
    private int daysInMonth;
    /** Avg daily sales needed from as-of date through month end to hit target */
    private BigDecimal catchUpDailyAverage;
}
