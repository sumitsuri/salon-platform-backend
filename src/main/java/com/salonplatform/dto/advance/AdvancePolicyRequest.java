package com.salonplatform.dto.advance;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdvancePolicyRequest {
    @NotNull
    @DecimalMin("0")
    @DecimalMax("100000000")
    private BigDecimal poolAmount;
    @Min(1)
    @Max(100)
    private int maxPoolSharePercent;
    @NotNull
    @DecimalMin("0.1")
    @DecimalMax("12")
    private BigDecimal maxSalaryMultiple;
    @Min(1)
    @Max(24)
    private int maxInstallments;
    @Min(0)
    @Max(365)
    private int cooldownDays;
    private boolean enabled = true;
}
