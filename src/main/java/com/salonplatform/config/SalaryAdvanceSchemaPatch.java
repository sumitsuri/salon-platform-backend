package com.salonplatform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * New leave-decision columns, and the widened status constraint, on an existing table. The new advance tables are created by Hibernate
 * (ddl-auto=update); this only makes the additive column change explicit and idempotent.
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class SalaryAdvanceSchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE leave_records ADD COLUMN IF NOT EXISTS decision_note VARCHAR(500)");
            jdbcTemplate.execute("ALTER TABLE leave_records ADD COLUMN IF NOT EXISTS decided_at TIMESTAMP WITH TIME ZONE");
            // Hibernate's ddl-auto=update created a CHECK limiting status to the original values and never alters
            // an existing constraint, so the new CANCELLED value would be rejected by the database.
            jdbcTemplate.execute("ALTER TABLE leave_records DROP CONSTRAINT IF EXISTS leave_records_status_check");
            jdbcTemplate.execute("ALTER TABLE leave_records ADD CONSTRAINT leave_records_status_check "
                    + "CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))");
            log.info("Salary advance / leave decision schema patch applied");
        } catch (Exception e) {
            log.warn("Salary advance schema patch skipped or partial: {}", e.getMessage());
        }
    }
}
