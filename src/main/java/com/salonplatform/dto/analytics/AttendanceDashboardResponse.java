package com.salonplatform.dto.analytics;

import com.salonplatform.dto.attendance.AttendanceResponse;
import com.salonplatform.dto.leave.LeaveResponse;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class AttendanceDashboardResponse {
    private long totalStaff;
    private long presentToday;
    private long onLeaveToday;
    private long absentToday;
    private BigDecimal avgHoursPerStaff;
    private List<DailyAttendanceTrend> dailyTrends;
    private List<StaffAttendanceSummary> staffSummaries;
    private List<AttendanceResponse> recentRecords;
    private List<LeaveResponse> leaves;
}
