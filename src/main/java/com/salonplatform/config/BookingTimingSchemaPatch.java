package com.salonplatform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Timing columns for floor availability and service-duration analytics.
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class BookingTimingSchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS service_started_at TIMESTAMP WITH TIME ZONE");
            jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS estimated_end_at TIMESTAMP WITH TIME ZONE");
            jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS actual_duration_minutes INTEGER");

            jdbcTemplate.execute("ALTER TABLE booking_line_items ADD COLUMN IF NOT EXISTS estimated_duration_minutes INTEGER");
            jdbcTemplate.execute("ALTER TABLE booking_line_items ADD COLUMN IF NOT EXISTS started_at TIMESTAMP WITH TIME ZONE");
            jdbcTemplate.execute("ALTER TABLE booking_line_items ADD COLUMN IF NOT EXISTS ended_at TIMESTAMP WITH TIME ZONE");
            jdbcTemplate.execute("ALTER TABLE booking_line_items ADD COLUMN IF NOT EXISTS actual_duration_minutes INTEGER");

            // Backfill open/completed visits so the floor board has usable blocks immediately.
            jdbcTemplate.execute("""
                    UPDATE bookings b
                    SET service_started_at = COALESCE(service_started_at, created_at),
                        estimated_end_at = COALESCE(
                            estimated_end_at,
                            created_at + INTERVAL '45 minutes'
                        )
                    WHERE service_started_at IS NULL OR estimated_end_at IS NULL
                    """);

            jdbcTemplate.execute("""
                    UPDATE booking_line_items li
                    SET estimated_duration_minutes = COALESCE(estimated_duration_minutes, 30),
                        started_at = COALESCE(started_at, (
                            SELECT b.service_started_at FROM bookings b WHERE b.id = li.booking_id
                        ))
                    WHERE estimated_duration_minutes IS NULL OR started_at IS NULL
                    """);

            jdbcTemplate.execute("""
                    UPDATE bookings
                    SET actual_duration_minutes = GREATEST(
                        1,
                        CAST(EXTRACT(EPOCH FROM (completed_at - COALESCE(service_started_at, created_at))) / 60 AS INTEGER)
                    )
                    WHERE completed_at IS NOT NULL AND actual_duration_minutes IS NULL
                    """);

            log.info("Booking timing schema patch applied");
        } catch (Exception e) {
            log.warn("Booking timing schema patch skipped or partial: {}", e.getMessage());
        }
    }
}
