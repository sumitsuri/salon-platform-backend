package com.salonplatform.dto.staffportal;

import com.salonplatform.domain.enums.StaffGoalStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class StaffGoalResponse {
    private UUID id;
    private String title;
    private String description;
    private String metricUnit;
    private BigDecimal targetValue;
    private BigDecimal currentValue;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private StaffGoalStatus status;
    private BigDecimal progressPercent;
}
