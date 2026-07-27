package com.salonplatform.sales.domain.port;

import java.time.Instant;

public record TenantSnapshot(
        long activeCount,
        long trialCount,
        long totalCount,
        Instant asOf
) {}
