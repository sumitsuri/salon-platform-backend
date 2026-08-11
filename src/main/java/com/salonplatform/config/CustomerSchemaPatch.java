package com.salonplatform.config;

import com.salonplatform.util.VisitPassUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Adds visit-pass identity columns and relaxes phone uniqueness for pass-only customers.
 * Safe for existing production rows: backfills visit_pass_id before enforcing NOT NULL.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class CustomerSchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE branches ADD COLUMN IF NOT EXISTS phone_number_required BOOLEAN NOT NULL DEFAULT true");
            // Mystic Wellness Varthur (MW01): phone optional for walk-in registration.
            jdbcTemplate.update(
                    "UPDATE branches SET phone_number_required = false "
                            + "WHERE lower(code) = 'mw01' "
                            + "AND tenant_id IN (SELECT id FROM tenants WHERE lower(slug) = 'mystic-wellness')");

            jdbcTemplate.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS visit_pass_id VARCHAR(32)");
            jdbcTemplate.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS identity_status VARCHAR(24)");
            jdbcTemplate.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS pass_public_token VARCHAR(64)");

            jdbcTemplate.execute("ALTER TABLE customers ALTER COLUMN visit_pass_id TYPE VARCHAR(32)");

            backfillVisitPassIds();

            jdbcTemplate.execute(
                    "UPDATE customers SET identity_status = 'PHONE_VERIFIED' "
                            + "WHERE identity_status IS NULL AND phone IS NOT NULL AND trim(phone) <> ''");
            jdbcTemplate.execute(
                    "UPDATE customers SET identity_status = 'PASS_ONLY' "
                            + "WHERE identity_status IS NULL");

            jdbcTemplate.execute("ALTER TABLE customers ALTER COLUMN phone DROP NOT NULL");

            dropConstraintIfExists("customers", "customers_tenant_id_phone_key");
            dropConstraintIfExists("customers", "uk_customers_tenant_phone");

            jdbcTemplate.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS uq_customers_tenant_visit_pass "
                            + "ON customers (tenant_id, visit_pass_id)");
            jdbcTemplate.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS uq_customers_tenant_phone_not_null "
                            + "ON customers (tenant_id, phone) WHERE phone IS NOT NULL AND trim(phone) <> ''");
            jdbcTemplate.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS uq_customers_pass_public_token "
                            + "ON customers (pass_public_token) WHERE pass_public_token IS NOT NULL");

            log.info("Customer visit-pass schema patch applied");
        } catch (Exception e) {
            log.warn("Customer schema patch skipped or partial: {}", e.getMessage());
        }
    }

    private void backfillVisitPassIds() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT c.id, c.tenant_id, t.slug AS tenant_slug "
                        + "FROM customers c "
                        + "JOIN tenants t ON t.id = c.tenant_id "
                        + "WHERE c.visit_pass_id IS NULL OR trim(c.visit_pass_id) = ''");
        for (Map<String, Object> row : rows) {
            Object id = row.get("id");
            String slug = row.get("tenant_slug") != null ? row.get("tenant_slug").toString() : "tenant";
            String prefix = derivePrefix(slug);
            String passId = uniquePassId(prefix, VisitPassUtils.FALLBACK_BRANCH_CODE, id.toString());
            jdbcTemplate.update(
                    "UPDATE customers SET visit_pass_id = ?, pass_public_token = COALESCE(pass_public_token, ?) "
                            + "WHERE id = ?::uuid",
                    passId,
                    randomToken(),
                    id.toString());
        }
    }

    private String uniquePassId(String prefix, String branchCode, String customerId) {
        String branch = VisitPassUtils.normalizeBranchCode(branchCode);
        for (int attempt = 0; attempt < 20; attempt++) {
            int n = ThreadLocalRandom.current().nextInt(100_000, 1_000_000);
            String candidate = prefix + "-" + branch + "-" + n;
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM customers WHERE visit_pass_id = ?",
                    Integer.class,
                    candidate);
            if (exists != null && exists == 0) {
                return candidate;
            }
        }
        int n = Math.floorMod(customerId.hashCode(), 900_000) + 100_000;
        return prefix + "-" + branch + "-" + n;
    }

    private static String derivePrefix(String slug) {
        if (slug == null || slug.isBlank()) {
            return "VP";
        }
        String[] parts = slug.trim().toLowerCase(Locale.ROOT).split("-");
        StringBuilder prefix = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                prefix.append(Character.toUpperCase(part.charAt(0)));
            }
            if (prefix.length() >= 3) {
                break;
            }
        }
        return prefix.length() >= 2 ? prefix.toString() : "VP";
    }

    private static String randomToken() {
        byte[] bytes = new byte[24];
        ThreadLocalRandom.current().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(48);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    private void dropConstraintIfExists(String table, String constraintName) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + constraintName);
        } catch (Exception ignored) {
            // constraint name varies by Hibernate version / manual DDL
        }
    }
}
