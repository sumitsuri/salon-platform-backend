package com.salonplatform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-time repair for bookings created before BookingService#create started setting createdAt
 * explicitly: @CreationTimestamp silently left it NULL on the pending-package/membership-plan
 * path, which drops the row from every date-range-filtered query (branch performance
 * drill-down, bookings list). service_started_at and scheduled_start_at are set directly in
 * application code on every creation path (never left to the Hibernate generator), so they are
 * a reliable stand-in for the missing value. Idempotent — only touches rows still NULL.
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class BookingCreatedAtBackfillSchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int updated = jdbcTemplate.update("""
                    UPDATE bookings
                    SET created_at = COALESCE(service_started_at, scheduled_start_at)
                    WHERE created_at IS NULL
                      AND COALESCE(service_started_at, scheduled_start_at) IS NOT NULL
                    """);
            log.info("Booking created_at backfill patch applied ({} row(s) repaired)", updated);
        } catch (Exception e) {
            log.warn("Booking created_at backfill patch skipped or partial: {}", e.getMessage());
        }
    }
}
