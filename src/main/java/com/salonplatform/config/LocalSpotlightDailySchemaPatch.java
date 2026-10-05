package com.salonplatform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LocalSpotlightDailySchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS local_spotlight_serp_cache (
                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        cache_key VARCHAR(160) NOT NULL,
                        snapshot_date DATE NOT NULL,
                        serp_json TEXT NOT NULL,
                        fetched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                        UNIQUE (cache_key, snapshot_date)
                    )""");
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS local_spotlight_keyword_rank_daily (
                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        tenant_id UUID NOT NULL,
                        branch_id UUID NOT NULL,
                        pin_code VARCHAR(6) NOT NULL,
                        keyword VARCHAR(255) NOT NULL,
                        snapshot_date DATE NOT NULL,
                        your_rank INT,
                        beyond_top_20 BOOLEAN NOT NULL DEFAULT false,
                        top_three_json TEXT,
                        recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                        UNIQUE (branch_id, keyword, snapshot_date)
                    )""");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_ls_rank_daily_branch_date "
                            + "ON local_spotlight_keyword_rank_daily (branch_id, snapshot_date)");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_ls_serp_cache_pin_date "
                            + "ON local_spotlight_serp_cache (cache_key, snapshot_date)");
            log.info("Local Spotlight daily rank schema patch applied");
        } catch (Exception e) {
            log.warn("Local Spotlight daily rank schema patch skipped or partial: {}", e.getMessage());
        }
    }
}
