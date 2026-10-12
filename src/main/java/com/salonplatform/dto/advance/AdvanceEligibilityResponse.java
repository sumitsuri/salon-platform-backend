package com.salonplatform.dto.advance;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** What the employee app shows before the form: can they ask, for how much, and if not why. */
@Data
@Builder
public class AdvanceEligibilityResponse {
    private boolean canRequest;
    /** NONE, NOT_CONFIGURED, DISABLED, PENDING_REQUEST, COOLDOWN, AT_LIMIT, POOL_EXHAUSTED, NO_SALARY. */
    private String blockedReason;
    private BigDecimal maxAmount;
    private int maxInstallments;
    private BigDecimal outstanding;
    private LocalDate cooldownEndsOn;
    private LocalDate firstDeductionMonth;
}
