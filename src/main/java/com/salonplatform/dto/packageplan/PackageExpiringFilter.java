package com.salonplatform.dto.packageplan;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class PackageExpiringFilter {
    private UUID branchId;
    /** Days ahead from today (inclusive window). Ignored when {@code expiresTo} is set. */
    private Integer withinDays;
    /** Inclusive expiry window start (defaults to today in IST). */
    private LocalDate expiresFrom;
    /** Inclusive expiry window end. */
    private LocalDate expiresTo;
    private int page;
    private int size;
}
