package com.salonplatform.dto.advance;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** The shared budget at a glance: what is set aside, what is out, what is left, and what is waiting for a decision. */
@Data
@Builder
public class AdvanceBudgetResponse {
    private AdvancePolicyResponse policy;
    private BigDecimal poolAmount;
    /** Approved money not yet recovered from salaries. */
    private BigDecimal outstanding;
    private BigDecimal available;
    private BigDecimal pendingRequested;
    private int pendingCount;
    /** Share of the pool currently out, 0-100. */
    private int utilizationPct;
    /** The most any one employee may hold at once (pool × share limit). */
    private BigDecimal perEmployeeCap;
    /** Installments falling due in the current salary month. */
    private BigDecimal dueThisMonth;
}
