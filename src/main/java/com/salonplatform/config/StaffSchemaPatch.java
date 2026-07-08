package com.salonplatform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibernate ddl-auto=update can fail when adding NOT NULL columns to tables with existing rows.
 * This patch ensures staff compensation columns exist before the app serves traffic.
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class StaffSchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE staff ADD COLUMN IF NOT EXISTS salary NUMERIC(19, 2)");
            jdbcTemplate.execute("ALTER TABLE staff ADD COLUMN IF NOT EXISTS joining_date DATE");
            jdbcTemplate.execute(
                    "ALTER TABLE staff ADD COLUMN IF NOT EXISTS id_proof_collected BOOLEAN DEFAULT FALSE");
            jdbcTemplate.execute("UPDATE staff SET id_proof_collected = FALSE WHERE id_proof_collected IS NULL");
            jdbcTemplate.execute("ALTER TABLE staff ADD COLUMN IF NOT EXISTS id_proof_reference VARCHAR(255)");
            jdbcTemplate.execute("ALTER TABLE staff ADD COLUMN IF NOT EXISTS monthly_sales_target NUMERIC(19, 2)");
            jdbcTemplate.execute("ALTER TABLE staff ADD COLUMN IF NOT EXISTS incentive_percent NUMERIC(19, 2)");
            backfillDemoStaff();
            log.info("Staff schema patch applied");
        } catch (Exception e) {
            log.warn("Staff schema patch skipped or partial: {}", e.getMessage());
        }
    }

    private void backfillDemoStaff() {
        jdbcTemplate.update(
                "UPDATE staff SET salary = 25000, joining_date = '2024-03-01', id_proof_collected = TRUE, "
                        + "id_proof_reference = 'Aadhaar XXXX4521', monthly_sales_target = 120000, incentive_percent = 5 "
                        + "WHERE biometric_id = 'FP-AMIT-LITHOS' AND salary IS NULL");
        jdbcTemplate.update(
                "UPDATE staff SET salary = 28000, joining_date = '2023-08-15', id_proof_collected = TRUE, "
                        + "id_proof_reference = 'PAN XXXX7890', monthly_sales_target = 150000, incentive_percent = 5 "
                        + "WHERE biometric_id = 'FP-PRIYA-LITHOS' AND salary IS NULL");
        jdbcTemplate.update(
                "UPDATE staff SET salary = 22000, joining_date = '2024-06-01', id_proof_collected = TRUE, "
                        + "id_proof_reference = 'Aadhaar XXXX3312', monthly_sales_target = 100000, incentive_percent = 4 "
                        + "WHERE biometric_id = 'FP-RAVI-WEBCITY' AND salary IS NULL");
        jdbcTemplate.update(
                "UPDATE staff SET salary = 24000, joining_date = '2024-01-10', id_proof_collected = FALSE, "
                        + "monthly_sales_target = 110000, incentive_percent = 4 "
                        + "WHERE biometric_id = 'FP-SNEHA-WEBCITY' AND salary IS NULL");
    }
}
