package com.salonplatform.dto.staffportal;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class StaffGrowthSnapshotResponse {
    private String periodLabel;
    private BigDecimal monthlySalesTarget;
    private BigDecimal actualSales;
    private BigDecimal achievementPercent;
    private boolean meetingTarget;
    private boolean onTrack;
    private long salesCount;
    private BigDecimal avgTicketSize;
    private BigDecimal projectedIncentive;
    private BigDecimal attendanceComplianceScore;
    private long daysPresent;
    private long daysAbsent;
    private BigDecimal todaySales;
    private long todaySalesCount;
}
