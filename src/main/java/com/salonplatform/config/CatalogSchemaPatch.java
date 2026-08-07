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
public class CatalogSchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE service_categories ADD COLUMN IF NOT EXISTS parent_category_id UUID");
            jdbcTemplate.execute(
                    "ALTER TABLE branch_services ADD COLUMN IF NOT EXISTS manual_price_override BOOLEAN DEFAULT FALSE");
            jdbcTemplate.execute(
                    "ALTER TABLE tenants ADD COLUMN IF NOT EXISTS catalog_patch_version VARCHAR(64)");
            jdbcTemplate.execute(
                    "ALTER TABLE services ADD COLUMN IF NOT EXISTS variable_pricing BOOLEAN DEFAULT FALSE");
            log.info("Catalog schema patch applied");
        } catch (Exception e) {
            log.warn("Catalog schema patch skipped or partial: {}", e.getMessage());
        }
    }
}
