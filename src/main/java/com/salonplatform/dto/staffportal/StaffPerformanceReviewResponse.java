package com.salonplatform.dto.staffportal;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class StaffPerformanceReviewResponse {
    private UUID id;
    private String periodLabel;
    private LocalDate reviewDate;
    private BigDecimal overallRating;
    private String strengths;
    private String improvements;
    private String managerNotes;
    private BigDecimal attendanceScore;
    private BigDecimal salesAchievementPercent;
}
