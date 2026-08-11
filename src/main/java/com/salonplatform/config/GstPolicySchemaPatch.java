package com.salonplatform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * GST is off by default for all brands; enable per brand or branch when needed.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class GstPolicySchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE tenants ADD COLUMN IF NOT EXISTS gst_enabled BOOLEAN DEFAULT FALSE");
            jdbcTemplate.update("UPDATE tenants SET gst_enabled = FALSE WHERE gst_enabled IS NULL");
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS gst_enabled BOOLEAN");
            // Mystic Wellness: GST off by brand policy (prod sign-off Aug 2026).
            jdbcTemplate.update(
                    "UPDATE tenants SET gst_enabled = FALSE WHERE lower(slug) = 'mystic-wellness'");
            jdbcTemplate.update(
                    "UPDATE branches SET gst_enabled = FALSE "
                            + "WHERE tenant_id IN (SELECT id FROM tenants WHERE lower(slug) = 'mystic-wellness')");
            log.info("GST policy schema patch applied (default off; mystic-wellness explicitly off)");
        } catch (Exception e) {
            log.warn("GST policy schema patch skipped or partial: {}", e.getMessage());
        }
    }
}
