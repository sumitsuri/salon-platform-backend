package com.salonplatform.aigateway.ledger;

import java.math.BigDecimal;
import java.time.Instant;

/** What one person and their business have used so far, for limit checks. */
public record UsageSnapshot(
        int requestsUserToday,
        int requestsTenantToday,
        BigDecimal spendTenantToday,
        BigDecimal spendTenantMonth,
        int violationsLastHour,
        Instant lastViolationAt) {

    public static UsageSnapshot empty() {
        return new UsageSnapshot(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, null);
    }
}
