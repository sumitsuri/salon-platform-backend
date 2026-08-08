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
public class InvoiceMembershipFeeSchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE invoices ADD COLUMN IF NOT EXISTS membership_fee_amount NUMERIC(14,2) DEFAULT 0");
            jdbcTemplate.execute(
                    "ALTER TABLE invoices ADD COLUMN IF NOT EXISTS membership_fee_label VARCHAR(255)");
            log.info("Invoice membership fee schema patch applied");
        } catch (Exception e) {
            log.warn("Invoice membership fee schema patch skipped or partial: {}", e.getMessage());
        }
    }
}
