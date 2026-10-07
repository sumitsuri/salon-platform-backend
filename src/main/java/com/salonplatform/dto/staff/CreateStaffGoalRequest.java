package com.salonplatform.dto.staff;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateStaffGoalRequest {
    @NotBlank
    private String title;
    private String description;
    private String metricUnit;
    private BigDecimal targetValue;
    private BigDecimal currentValue;
    private LocalDate periodStart;
    private LocalDate periodEnd;
}
