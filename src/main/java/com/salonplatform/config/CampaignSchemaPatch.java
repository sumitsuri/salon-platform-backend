package com.salonplatform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Campaign growth features: extended filters, ACTIVE/ARCHIVED status, send runs.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class CampaignSchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE marketing_campaigns ADD COLUMN IF NOT EXISTS template_id VARCHAR(80)");
            jdbcTemplate.execute("ALTER TABLE marketing_campaigns ADD COLUMN IF NOT EXISTS filter_branch_id UUID");
            jdbcTemplate.execute("ALTER TABLE marketing_campaigns ADD COLUMN IF NOT EXISTS filter_membership_filter VARCHAR(30)");
            jdbcTemplate.execute("ALTER TABLE marketing_campaigns ADD COLUMN IF NOT EXISTS filter_membership_expiring_within_days INTEGER");
            jdbcTemplate.execute("ALTER TABLE marketing_campaigns ADD COLUMN IF NOT EXISTS filter_has_service_ids TEXT");
            jdbcTemplate.execute("ALTER TABLE marketing_campaigns ADD COLUMN IF NOT EXISTS filter_exclude_service_ids TEXT");
            jdbcTemplate.execute("ALTER TABLE marketing_campaigns ADD COLUMN IF NOT EXISTS filter_has_service_category_ids TEXT");
            jdbcTemplate.execute("ALTER TABLE marketing_campaigns ADD COLUMN IF NOT EXISTS filter_exclude_service_category_ids TEXT");
            jdbcTemplate.execute("ALTER TABLE marketing_campaigns ADD COLUMN IF NOT EXISTS filter_max_overall_rating INTEGER");
            jdbcTemplate.execute("ALTER TABLE marketing_campaigns ADD COLUMN IF NOT EXISTS filter_min_overall_rating INTEGER");
            jdbcTemplate.execute("ALTER TABLE marketing_campaigns ADD COLUMN IF NOT EXISTS filter_has_submitted_review BOOLEAN");
            jdbcTemplate.execute("ALTER TABLE marketing_campaigns ADD COLUMN IF NOT EXISTS filter_google_review_not_submitted BOOLEAN");
            jdbcTemplate.execute("ALTER TABLE marketing_campaigns ADD COLUMN IF NOT EXISTS filter_booking_source VARCHAR(20)");

            jdbcTemplate.execute("ALTER TABLE marketing_campaigns DROP CONSTRAINT IF EXISTS marketing_campaigns_status_check");
            jdbcTemplate.execute("""
                    ALTER TABLE marketing_campaigns ADD CONSTRAINT marketing_campaigns_status_check
                    CHECK (status IN ('ACTIVE', 'DRAFT', 'SENDING', 'COMPLETED', 'FAILED', 'ARCHIVED'))
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS campaign_runs (
                        id UUID PRIMARY KEY,
                        tenant_id UUID NOT NULL,
                        campaign_id UUID NOT NULL,
                        status VARCHAR(20) NOT NULL DEFAULT 'SENDING',
                        recipient_count INTEGER NOT NULL DEFAULT 0,
                        sent_count INTEGER NOT NULL DEFAULT 0,
                        failed_count INTEGER NOT NULL DEFAULT 0,
                        started_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                        completed_at TIMESTAMP WITH TIME ZONE
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_campaign_runs_campaign
                    ON campaign_runs (campaign_id, started_at DESC)
                    """);

            jdbcTemplate.execute("ALTER TABLE message_delivery_logs ADD COLUMN IF NOT EXISTS campaign_run_id UUID");
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_message_delivery_logs_campaign_run
                    ON message_delivery_logs (campaign_run_id)
                    """);

            log.info("Campaign schema patch applied");
        } catch (Exception e) {
            log.warn("Campaign schema patch skipped or partial: {}", e.getMessage());
        }
    }
}
