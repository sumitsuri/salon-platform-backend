package com.salonplatform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class SalesSchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
            jdbcTemplate.execute("""
                    ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (
                        role IN (
                            'PLATFORM_SUPER_ADMIN',
                            'SALES_EXECUTIVE',
                            'BRAND_ADMIN',
                            'BRANCH_MANAGER',
                            'SALON_MANAGER'
                        )
                    )
                    """);
        } catch (Exception e) {
            log.warn("User role constraint patch failed: {}", e.getMessage());
        }
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS sales_leads (
                        id UUID PRIMARY KEY,
                        business_name VARCHAR(255) NOT NULL,
                        contact_name VARCHAR(255) NOT NULL,
                        email VARCHAR(255),
                        phone VARCHAR(32) NOT NULL,
                        lead_type VARCHAR(32) NOT NULL,
                        stage VARCHAR(32) NOT NULL,
                        source VARCHAR(32) NOT NULL,
                        locality_id UUID,
                        locality_name VARCHAR(255),
                        address VARCHAR(512),
                        city VARCHAR(128),
                        expected_branches INT DEFAULT 1,
                        use_case TEXT,
                        notes TEXT,
                        assigned_rep_id UUID,
                        converted_tenant_id UUID,
                        projected_mrr NUMERIC(19, 2),
                        plan_tier VARCHAR(64),
                        lost_reason TEXT,
                        trial_intent_at TIMESTAMP,
                        converted_at TIMESTAMP,
                        next_follow_up_at TIMESTAMP,
                        created_at TIMESTAMP,
                        updated_at TIMESTAMP
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS sales_activities (
                        id UUID PRIMARY KEY,
                        lead_id UUID NOT NULL,
                        rep_id UUID NOT NULL,
                        activity_type VARCHAR(32) NOT NULL,
                        notes TEXT,
                        activity_at TIMESTAMP,
                        created_at TIMESTAMP
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS sales_stage_history (
                        id UUID PRIMARY KEY,
                        lead_id UUID NOT NULL,
                        from_stage VARCHAR(32),
                        to_stage VARCHAR(32) NOT NULL,
                        changed_by_user_id UUID NOT NULL,
                        notes TEXT,
                        created_at TIMESTAMP
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS sales_targets (
                        id UUID PRIMARY KEY,
                        rep_id UUID NOT NULL,
                        week_start_date DATE NOT NULL,
                        target_leads INT DEFAULT 0,
                        target_visits INT DEFAULT 0,
                        target_pitches INT DEFAULT 0,
                        target_trials INT DEFAULT 0,
                        target_conversions INT DEFAULT 0,
                        created_at TIMESTAMP,
                        updated_at TIMESTAMP,
                        UNIQUE(rep_id, week_start_date)
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS sales_incentive_rules (
                        id UUID PRIMARY KEY,
                        event_type VARCHAR(32) NOT NULL,
                        amount_inr NUMERIC(19, 2) NOT NULL,
                        active BOOLEAN DEFAULT TRUE,
                        created_at TIMESTAMP,
                        updated_at TIMESTAMP
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS sales_incentive_ledger (
                        id UUID PRIMARY KEY,
                        rep_id UUID NOT NULL,
                        lead_id UUID NOT NULL,
                        event_type VARCHAR(32) NOT NULL,
                        amount_inr NUMERIC(19, 2) NOT NULL,
                        week_start_date DATE NOT NULL,
                        computed_at TIMESTAMP
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS sales_localities (
                        id UUID PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        zone VARCHAR(64),
                        active BOOLEAN DEFAULT TRUE,
                        created_at TIMESTAMP
                    )
                    """);
        } catch (Exception e) {
            log.warn("SalesSchemaPatch failed: {}", e.getMessage());
        }
        try {
            jdbcTemplate.execute("ALTER TABLE sales_leads ADD COLUMN IF NOT EXISTS quoted_amount NUMERIC(19, 2)");
            jdbcTemplate.execute("ALTER TABLE sales_leads ADD COLUMN IF NOT EXISTS billing_period VARCHAR(16)");
            jdbcTemplate.execute("ALTER TABLE sales_leads ADD COLUMN IF NOT EXISTS discount_percent NUMERIC(5, 2)");
            jdbcTemplate.execute("ALTER TABLE sales_leads ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(19, 2)");
            jdbcTemplate.execute("ALTER TABLE sales_leads ADD COLUMN IF NOT EXISTS final_paid_amount NUMERIC(19, 2)");
        } catch (Exception e) {
            log.warn("Sales leads pricing columns patch failed: {}", e.getMessage());
        }
    }
}
