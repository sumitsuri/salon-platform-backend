package com.salonplatform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class LocalSpotlightFeatureSchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE tenants ADD COLUMN IF NOT EXISTS local_spotlight_enabled BOOLEAN");
            jdbcTemplate.execute(
                    "ALTER TABLE tenants ADD COLUMN IF NOT EXISTS guest_reviews_enabled BOOLEAN");
            jdbcTemplate.execute(
                    "ALTER TABLE branches ADD COLUMN IF NOT EXISTS local_spotlight_google_sync_enabled BOOLEAN");

            jdbcTemplate.update(
                    "UPDATE tenants SET local_spotlight_enabled = false WHERE local_spotlight_enabled IS NULL");
            jdbcTemplate.update(
                    "UPDATE tenants SET guest_reviews_enabled = false WHERE guest_reviews_enabled IS NULL");
            jdbcTemplate.update(
                    "UPDATE branches SET local_spotlight_google_sync_enabled = false "
                            + "WHERE local_spotlight_google_sync_enabled IS NULL");

            jdbcTemplate.update(
                    "UPDATE tenants SET local_spotlight_enabled = true, guest_reviews_enabled = true "
                            + "WHERE lower(slug) IN ('mystic-wellness', 'mango-wellness') "
                            + "AND COALESCE(demo_tenant, false) = false");

            // Google Places sync per branch (replaces single global SSM pilot branch).
            jdbcTemplate.update(
                    "UPDATE branches SET local_spotlight_google_sync_enabled = true "
                            + "WHERE upper(code) = 'MW01' "
                            + "AND tenant_id IN (SELECT id FROM tenants WHERE lower(slug) = 'mystic-wellness')");
            jdbcTemplate.update(
                    "UPDATE branches SET local_spotlight_google_sync_enabled = true "
                            + "WHERE upper(code) = 'MS001' "
                            + "AND tenant_id IN (SELECT id FROM tenants WHERE lower(slug) = 'mango-wellness')");
            jdbcTemplate.update(
                    "UPDATE branches SET local_spotlight_google_sync_enabled = true "
                            + "WHERE google_place_id IS NOT NULL AND trim(google_place_id) <> '' "
                            + "AND tenant_id IN (SELECT id FROM tenants WHERE COALESCE(local_spotlight_enabled, false) = true)");

            log.info("Local Spotlight feature schema patch applied");
        } catch (Exception e) {
            log.warn("Local Spotlight feature schema patch skipped or partial: {}", e.getMessage());
        }
    }
}
