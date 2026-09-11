package com.salonplatform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Customers belong to a single branch. Backfills branch_id and replaces tenant-wide unique indexes.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class CustomerBranchScopePatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS branch_id UUID");

            jdbcTemplate.update(
                    """
                    UPDATE customers c
                    SET branch_id = sub.branch_id
                    FROM (
                      SELECT DISTINCT ON (b.customer_id) b.customer_id, b.branch_id
                      FROM bookings b
                      ORDER BY b.customer_id, b.created_at ASC
                    ) sub
                    WHERE c.id = sub.customer_id AND c.branch_id IS NULL
                    """);

            jdbcTemplate.update(
                    """
                    UPDATE customers c
                    SET branch_id = (
                      SELECT b.id FROM branches b
                      WHERE b.tenant_id = c.tenant_id
                      ORDER BY b.created_at ASC NULLS LAST, b.id ASC
                      LIMIT 1
                    )
                    WHERE c.branch_id IS NULL
                    """);

            jdbcTemplate.execute(
                    """
                    DO $$
                    BEGIN
                      IF NOT EXISTS (
                        SELECT 1 FROM pg_constraint WHERE conname = 'fk_customers_branch'
                      ) THEN
                        ALTER TABLE customers
                          ADD CONSTRAINT fk_customers_branch
                          FOREIGN KEY (branch_id) REFERENCES branches(id);
                      END IF;
                    END $$
                    """);

            jdbcTemplate.execute("ALTER TABLE customers ALTER COLUMN branch_id SET NOT NULL");

            dropConstraintIfExists("customers", "customers_tenant_id_phone_key");
            dropConstraintIfExists("customers", "uk_customers_tenant_phone");

            jdbcTemplate.execute("DROP INDEX IF EXISTS uq_customers_tenant_phone_not_null");
            jdbcTemplate.execute("DROP INDEX IF EXISTS uq_customers_tenant_visit_pass");
            dropLegacyTenantWideCustomerUniques();

            jdbcTemplate.execute(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS uq_customers_branch_phone_not_null
                    ON customers (branch_id, phone)
                    WHERE phone IS NOT NULL AND trim(phone) <> ''
                    """);
            jdbcTemplate.execute(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS uq_customers_branch_visit_pass
                    ON customers (branch_id, visit_pass_id)
                    """);

            log.info("Customer branch scope schema patch applied");
        } catch (Exception e) {
            log.warn("Customer branch scope patch skipped or partial: {}", e.getMessage());
        }
    }

    private void dropConstraintIfExists(String table, String constraintName) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + constraintName);
        } catch (Exception ignored) {
            // constraint name varies by Hibernate version / manual DDL
        }
    }

    /** Drops Hibernate / legacy UNIQUE on (tenant_id, phone) or (tenant_id, visit_pass_id). */
    private void dropLegacyTenantWideCustomerUniques() {
        jdbcTemplate.execute(
                """
                DO $$
                DECLARE r record;
                BEGIN
                  FOR r IN
                    SELECT c.conname
                    FROM pg_constraint c
                    JOIN pg_class t ON c.conrelid = t.oid
                    WHERE t.relname = 'customers'
                      AND c.contype = 'u'
                      AND (
                        pg_get_constraintdef(c.oid) LIKE '%tenant_id%phone%'
                        OR pg_get_constraintdef(c.oid) LIKE '%tenant_id%visit_pass%'
                      )
                      AND pg_get_constraintdef(c.oid) NOT LIKE '%branch_id%'
                  LOOP
                    EXECUTE 'ALTER TABLE customers DROP CONSTRAINT IF EXISTS ' || quote_ident(r.conname);
                  END LOOP;
                END $$
                """);
        jdbcTemplate.execute(
                """
                DO $$
                DECLARE r record;
                BEGIN
                  FOR r IN
                    SELECT indexname
                    FROM pg_indexes
                    WHERE schemaname = 'public'
                      AND tablename = 'customers'
                      AND indexdef LIKE '%UNIQUE%'
                      AND indexdef LIKE '%tenant_id%'
                      AND (
                        indexdef LIKE '%phone%'
                        OR indexdef LIKE '%visit_pass%'
                      )
                      AND indexdef NOT LIKE '%branch_id%'
                  LOOP
                    EXECUTE 'DROP INDEX IF EXISTS ' || quote_ident(r.indexname);
                  END LOOP;
                END $$
                """);
    }
}
