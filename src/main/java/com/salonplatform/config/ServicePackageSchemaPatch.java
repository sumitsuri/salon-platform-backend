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
public class ServicePackageSchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS service_package_plans (
                      id UUID PRIMARY KEY,
                      tenant_id UUID NOT NULL,
                      name VARCHAR(120) NOT NULL,
                      description TEXT,
                      list_price_total NUMERIC(12,2) NOT NULL,
                      package_price NUMERIC(12,2) NOT NULL,
                      validity_days INT NOT NULL DEFAULT 90,
                      redemption_mode VARCHAR(24) NOT NULL,
                      branch_ids VARCHAR(2000),
                      status VARCHAR(20) NOT NULL,
                      predefined_rank INT,
                      created_by_user_id UUID,
                      created_at TIMESTAMPTZ,
                      updated_at TIMESTAMPTZ
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS service_package_plan_items (
                      id UUID PRIMARY KEY,
                      plan_id UUID NOT NULL REFERENCES service_package_plans(id) ON DELETE CASCADE,
                      service_id UUID NOT NULL,
                      quantity INT NOT NULL DEFAULT 1,
                      sort_order INT NOT NULL DEFAULT 0
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS customer_package_subscriptions (
                      id UUID PRIMARY KEY,
                      tenant_id UUID NOT NULL,
                      customer_id UUID NOT NULL,
                      branch_id UUID NOT NULL,
                      plan_id UUID NOT NULL,
                      plan_name VARCHAR(120) NOT NULL,
                      redemption_mode VARCHAR(24) NOT NULL,
                      amount_paid NUMERIC(12,2) NOT NULL,
                      purchase_invoice_id UUID,
                      purchase_booking_id UUID,
                      purchased_on DATE NOT NULL,
                      expires_on DATE NOT NULL,
                      status VARCHAR(20) NOT NULL,
                      sold_by_user_id UUID,
                      created_at TIMESTAMPTZ
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS customer_package_entitlements (
                      id UUID PRIMARY KEY,
                      subscription_id UUID NOT NULL REFERENCES customer_package_subscriptions(id) ON DELETE CASCADE,
                      service_id UUID NOT NULL,
                      service_name VARCHAR(200) NOT NULL,
                      quantity_total INT NOT NULL,
                      quantity_remaining INT NOT NULL
                    )
                    """);
            jdbcTemplate.execute(
                    "ALTER TABLE bookings ADD COLUMN IF NOT EXISTS pending_package_plan_id UUID");
            jdbcTemplate.execute(
                    "ALTER TABLE booking_line_items ADD COLUMN IF NOT EXISTS package_subscription_id UUID");
            jdbcTemplate.execute(
                    "ALTER TABLE invoices ADD COLUMN IF NOT EXISTS package_fee_amount NUMERIC(14,2) DEFAULT 0");
            jdbcTemplate.execute(
                    "ALTER TABLE invoices ADD COLUMN IF NOT EXISTS package_fee_label VARCHAR(255)");
            jdbcTemplate.execute(
                    "ALTER TABLE invoices ADD COLUMN IF NOT EXISTS customer_package_subscription_id UUID");
            log.info("Service package schema patch applied");
        } catch (Exception e) {
            log.warn("Service package schema patch skipped or partial: {}", e.getMessage());
        }
    }
}
