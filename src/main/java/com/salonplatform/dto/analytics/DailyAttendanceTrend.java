package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class DailyAttendanceTrend {
    private LocalDate date;
    private long presentCount;
    private long leaveCount;
    private BigDecimal avgHours;
}
