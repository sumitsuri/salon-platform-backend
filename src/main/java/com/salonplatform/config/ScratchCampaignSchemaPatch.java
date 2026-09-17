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
public class ScratchCampaignSchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS scratch_campaigns (
                      id UUID PRIMARY KEY,
                      tenant_id UUID NOT NULL,
                      name VARCHAR(120) NOT NULL,
                      description TEXT,
                      status VARCHAR(20) NOT NULL,
                      starts_at TIMESTAMPTZ NOT NULL,
                      ends_at TIMESTAMPTZ NOT NULL,
                      cards_valid_days INT NOT NULL DEFAULT 14,
                      created_by_user_id UUID,
                      created_at TIMESTAMPTZ,
                      updated_at TIMESTAMPTZ
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS scratch_campaign_prizes (
                      id UUID PRIMARY KEY,
                      campaign_id UUID NOT NULL REFERENCES scratch_campaigns(id) ON DELETE CASCADE,
                      label VARCHAR(160) NOT NULL,
                      prize_kind VARCHAR(32) NOT NULL,
                      discount_type VARCHAR(20),
                      discount_value NUMERIC(12,2),
                      service_scope VARCHAR(20),
                      scope_ids VARCHAR(2000),
                      weight INT NOT NULL DEFAULT 1,
                      sort_order INT NOT NULL DEFAULT 0
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS scratch_cards (
                      id UUID PRIMARY KEY,
                      tenant_id UUID NOT NULL,
                      campaign_id UUID NOT NULL REFERENCES scratch_campaigns(id),
                      branch_id UUID NOT NULL,
                      public_token VARCHAR(64) NOT NULL UNIQUE,
                      booking_id UUID,
                      customer_id UUID,
                      prize_id UUID REFERENCES scratch_campaign_prizes(id),
                      status VARCHAR(20) NOT NULL,
                      customer_phone VARCHAR(20),
                      customer_name VARCHAR(120),
                      coupon_id UUID,
                      redemption_code VARCHAR(12),
                      issued_by_user_id UUID,
                      scratched_at TIMESTAMPTZ,
                      contact_captured_at TIMESTAMPTZ,
                      redeemed_at TIMESTAMPTZ,
                      redeemed_booking_id UUID,
                      expires_at TIMESTAMPTZ NOT NULL,
                      created_at TIMESTAMPTZ
                    )
                    """);
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_scratch_cards_token ON scratch_cards(public_token)");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_scratch_cards_redemption ON scratch_cards(tenant_id, redemption_code)");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_scratch_cards_booking ON scratch_cards(tenant_id, booking_id)");
            try {
                jdbcTemplate.execute("""
                        CREATE UNIQUE INDEX IF NOT EXISTS idx_scratch_cards_one_per_booking
                        ON scratch_cards (booking_id) WHERE booking_id IS NOT NULL
                        """);
            } catch (Exception uniqueEx) {
                log.warn(
                        "One scratch card per booking index not applied (duplicate booking_id rows may exist): {}",
                        uniqueEx.getMessage());
            }
        } catch (Exception e) {
            log.warn("Scratch campaign schema patch skipped: {}", e.getMessage());
        }
    }
}
