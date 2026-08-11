package com.salonplatform.domain.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Branch-scoped visit aggregates for a customer (completed bookings). */
public interface CustomerBranchStatsRow {
    UUID getCustomerId();

    Long getVisitCount();

    Instant getLastVisitAt();

    BigDecimal getLifetimeSpend();
}
