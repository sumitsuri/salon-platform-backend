package com.salonplatform.dto.advance;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class AdvancePolicyResponse {
    private BigDecimal poolAmount;
    private int maxPoolSharePercent;
    private BigDecimal maxSalaryMultiple;
    private int maxInstallments;
    private int cooldownDays;
    private boolean enabled;
    /** False until the owner has set a budget above zero. */
    private boolean configured;
    private Instant updatedAt;
}
