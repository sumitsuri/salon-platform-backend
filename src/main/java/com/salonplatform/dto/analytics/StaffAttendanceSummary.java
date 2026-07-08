package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class StaffAttendanceSummary {
    private String staffId;
    private String staffName;
    private String branchName;
    private long daysPresent;
    private long daysLeave;
    private BigDecimal totalHours;
    private BigDecimal avgHoursPerDay;
    private long lateArrivals;
    private BigDecimal performanceScore;
}
