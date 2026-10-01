package com.salonplatform.config;

import com.salonplatform.seed.SeedCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Backfills the brand-level demo flag and outbound messaging mode. Only NULL rows are touched, so
 * this is effectively one-shot: brands that existed before the column (e.g. mystic-wellness) stay
 * LIVE, the synthetic seed brands (demo-brand and its competitor set) move to SIMULATE.
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class TenantSandboxSchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE tenants ADD COLUMN IF NOT EXISTS demo_tenant BOOLEAN");
            jdbcTemplate.execute("ALTER TABLE tenants ADD COLUMN IF NOT EXISTS outbound_messaging_mode VARCHAR(16)");
            jdbcTemplate.update("UPDATE tenants SET demo_tenant = false WHERE demo_tenant IS NULL");

            String seedSlugs = String.join(",", SeedCatalog.slugs().stream().map(s -> "'" + s + "'").toList());
            int simulated = jdbcTemplate.update(
                    "UPDATE tenants SET outbound_messaging_mode = 'SIMULATE' "
                            + "WHERE outbound_messaging_mode IS NULL AND slug IN (" + seedSlugs + ")");
            int live = jdbcTemplate.update(
                    "UPDATE tenants SET outbound_messaging_mode = 'LIVE' WHERE outbound_messaging_mode IS NULL");
            log.info("Tenant sandbox schema patch applied (simulate={}, live={})", simulated, live);
        } catch (Exception e) {
            log.warn("Tenant sandbox schema patch skipped or partial: {}", e.getMessage());
        }
    }
}
