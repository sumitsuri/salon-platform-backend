package com.salonplatform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class BookingVoidSchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE");
            jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS void_reason TEXT");
            jdbcTemplate.execute("ALTER TABLE invoices ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE");
            jdbcTemplate.execute("ALTER TABLE invoices ADD COLUMN IF NOT EXISTS void_reason TEXT");
            log.info("Booking void / soft-delete schema patch applied");
        } catch (Exception e) {
            log.warn("Booking void schema patch skipped: {}", e.getMessage());
        }
    }
}
