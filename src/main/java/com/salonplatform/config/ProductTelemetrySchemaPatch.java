package com.salonplatform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(5)
@RequiredArgsConstructor
@Slf4j
public class ProductTelemetrySchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS product_events (
                        id UUID PRIMARY KEY,
                        occurred_at TIMESTAMPTZ NOT NULL,
                        received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        event_name VARCHAR(64) NOT NULL,
                        event_version SMALLINT NOT NULL DEFAULT 1,
                        tenant_id UUID,
                        branch_id UUID,
                        user_id UUID,
                        role VARCHAR(32) NOT NULL,
                        portal VARCHAR(16),
                        session_id UUID,
                        feature_key VARCHAR(128),
                        route VARCHAR(256),
                        properties JSONB,
                        client VARCHAR(16),
                        app_version VARCHAR(64),
                        client_event_id UUID
                    )
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS usage_daily_tenant (
                        tenant_id UUID NOT NULL,
                        usage_date DATE NOT NULL,
                        active_users INT NOT NULL DEFAULT 0,
                        active_staff INT NOT NULL DEFAULT 0,
                        active_managers INT NOT NULL DEFAULT 0,
                        session_count INT NOT NULL DEFAULT 0,
                        PRIMARY KEY (tenant_id, usage_date)
                    )
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS usage_daily_tenant_feature (
                        tenant_id UUID NOT NULL,
                        usage_date DATE NOT NULL,
                        feature_key VARCHAR(128) NOT NULL,
                        hit_count INT NOT NULL DEFAULT 0,
                        PRIMARY KEY (tenant_id, usage_date, feature_key)
                    )
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS usage_daily_user (
                        tenant_id UUID NOT NULL,
                        user_id UUID NOT NULL,
                        usage_date DATE NOT NULL,
                        event_count INT NOT NULL DEFAULT 0,
                        distinct_features INT NOT NULL DEFAULT 0,
                        last_event_at TIMESTAMPTZ,
                        PRIMARY KEY (tenant_id, user_id, usage_date)
                    )
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS user_usage_snapshot (
                        tenant_id UUID NOT NULL,
                        user_id UUID NOT NULL,
                        last_seen_at TIMESTAMPTZ,
                        last_portal VARCHAR(16),
                        last_feature_key VARCHAR(128),
                        last_route VARCHAR(256),
                        first_seen_at TIMESTAMPTZ,
                        login_count INT NOT NULL DEFAULT 0,
                        days_active_28d INT NOT NULL DEFAULT 0,
                        PRIMARY KEY (tenant_id, user_id)
                    )
                    """);

            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_product_events_tenant_occurred ON product_events (tenant_id, occurred_at DESC)");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_product_events_user_occurred ON product_events (user_id, occurred_at DESC)");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_product_events_tenant_name_occurred ON product_events (tenant_id, event_name, occurred_at DESC)");
            jdbcTemplate.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_product_events_client_dedupe
                    ON product_events (user_id, client_event_id)
                    WHERE client_event_id IS NOT NULL
                    """);

            log.info("Product telemetry schema patch applied");
        } catch (Exception e) {
            log.warn("Product telemetry schema patch skipped or partial: {}", e.getMessage());
        }
    }
}
