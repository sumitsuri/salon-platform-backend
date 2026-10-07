package com.salonplatform.dto.staffportal;

import com.salonplatform.dto.attendance.AttendanceResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StaffAttendanceMonthResponse {
    private int year;
    private int month;
    private String monthLabel;
    /** Inclusive range covered by summary and day rows (MTD when viewing current month). */
    private String periodLabel;
    private double presentDays;
    private double absentDays;
    private double halfDays;
    private long leaveDays;
    private String overtimeHours;
    private String lessHours;
    private List<AttendanceResponse> days;
}
