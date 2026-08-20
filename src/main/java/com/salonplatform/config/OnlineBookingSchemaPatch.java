package com.salonplatform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Online booking columns for branches and scheduled appointments on bookings.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class OnlineBookingSchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE tenants ADD COLUMN IF NOT EXISTS online_booking_enabled BOOLEAN DEFAULT FALSE");
            jdbcTemplate.execute("ALTER TABLE tenants ALTER COLUMN online_booking_enabled SET DEFAULT FALSE");
            jdbcTemplate.update("UPDATE tenants SET online_booking_enabled = FALSE WHERE online_booking_enabled IS NULL");

            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS online_booking_enabled BOOLEAN DEFAULT FALSE");
            jdbcTemplate.execute("ALTER TABLE branches ALTER COLUMN online_booking_enabled SET DEFAULT FALSE");
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS online_booking_min_lead_minutes INTEGER DEFAULT 60");
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS online_booking_max_advance_days INTEGER DEFAULT 30");
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS online_booking_slot_minutes INTEGER DEFAULT 15");

            jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS source VARCHAR(32) DEFAULT 'WALK_IN'");
            jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS scheduled_start_at TIMESTAMP WITH TIME ZONE");
            jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS scheduled_end_at TIMESTAMP WITH TIME ZONE");
            jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS manage_token VARCHAR(64)");

            jdbcTemplate.execute("ALTER TABLE bookings ALTER COLUMN created_by_user_id DROP NOT NULL");

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS customer_otp_challenges (
                        id UUID PRIMARY KEY,
                        tenant_id UUID NOT NULL,
                        phone VARCHAR(20) NOT NULL,
                        otp_hash VARCHAR(128) NOT NULL,
                        expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        verified_at TIMESTAMP WITH TIME ZONE,
                        created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
                    )
                    """);

            jdbcTemplate.update("UPDATE branches SET online_booking_enabled = FALSE WHERE online_booking_enabled IS NULL");
            jdbcTemplate.update("UPDATE bookings SET source = 'WALK_IN' WHERE source IS NULL");
            jdbcTemplate.update("""
                    UPDATE bookings SET source = 'ONLINE'
                    WHERE scheduled_start_at IS NOT NULL
                      AND created_by_user_id IS NULL
                      AND (source IS NULL OR source = 'WALK_IN')
                    """);

            jdbcTemplate.execute("ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_status_check");
            jdbcTemplate.execute("""
                    ALTER TABLE bookings ADD CONSTRAINT bookings_status_check
                    CHECK (status IN ('DRAFT', 'CONFIRMED', 'IN_PROGRESS', 'READY_FOR_BILLING', 'COMPLETED', 'CANCELLED'))
                    """);

            ensurePassOnlyCustomerSchema();

            log.info("Online booking schema patch applied");
        } catch (Exception e) {
            log.warn("Online booking schema patch skipped or partial: {}", e.getMessage());
        }
    }

    /** Idempotent — online/walk-in pass-only customers may omit phone (e.g. Varthur MW01). */
    private void ensurePassOnlyCustomerSchema() {
        try {
            jdbcTemplate.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS visit_pass_id VARCHAR(32)");
            jdbcTemplate.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS identity_status VARCHAR(24)");
            jdbcTemplate.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS pass_public_token VARCHAR(64)");
            jdbcTemplate.execute("ALTER TABLE customers ALTER COLUMN phone DROP NOT NULL");
            jdbcTemplate.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS uq_customers_tenant_visit_pass "
                            + "ON customers (tenant_id, visit_pass_id)");
            jdbcTemplate.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS uq_customers_tenant_phone_not_null "
                            + "ON customers (tenant_id, phone) WHERE phone IS NOT NULL AND trim(phone) <> ''");
            log.info("Pass-only customer schema ensured for online booking");
        } catch (Exception e) {
            log.warn("Pass-only customer schema patch partial: {}", e.getMessage());
        }
    }
}
