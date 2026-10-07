package com.salonplatform.dto.staff;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateStaffPerformanceReviewRequest {
    @NotBlank
    private String periodLabel;
    private LocalDate reviewDate;
    private BigDecimal overallRating;
    private String strengths;
    private String improvements;
    private String managerNotes;
    private BigDecimal attendanceScore;
    private BigDecimal salesAchievementPercent;
    private Boolean visibleToStaff;
}
