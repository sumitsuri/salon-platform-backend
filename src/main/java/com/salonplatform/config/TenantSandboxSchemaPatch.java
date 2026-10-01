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

    private static final String SEED_BRANDS_DEMO_ACTION = "SEED_BRANDS_MARKED_DEMO";

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

            // One-shot: the synthetic seed brands stop appearing as real brands' Market Pulse peers.
            // Recorded in audit_logs so a later manual change from platform admin is never overridden.
            Integer done = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM audit_logs WHERE action = ?", Integer.class, SEED_BRANDS_DEMO_ACTION);
            if (done != null && done == 0) {
                int flagged = jdbcTemplate.update(
                        "UPDATE tenants SET demo_tenant = true WHERE slug IN (" + seedSlugs + ")");
                jdbcTemplate.update("INSERT INTO audit_logs (id, action, entity_type, details, created_at) "
                                + "VALUES (?, ?, 'TENANT', ?, now())",
                        java.util.UUID.randomUUID(), SEED_BRANDS_DEMO_ACTION, "{\"flagged\":" + flagged + "}");
                log.info("Flagged {} synthetic seed brands as demo", flagged);
            }
        } catch (Exception e) {
            log.warn("Tenant sandbox schema patch skipped or partial: {}", e.getMessage());
        }
    }
}
