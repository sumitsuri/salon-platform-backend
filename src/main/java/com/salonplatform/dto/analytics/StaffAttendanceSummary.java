package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

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
    private long earlyExits;
    private long geoFlags;
    private BigDecimal performanceScore;
    private int complianceScore;
    /** Latest attendance record in the selected period (for overview check-in table). */
    private String attendanceRecordId;
    private Instant entryTime;
    private Instant exitTime;
    private boolean hasEntryPhoto;
    private boolean hasExitPhoto;
}
