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
public class BranchSchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS monthly_sales_target NUMERIC(19, 2)");
            jdbcTemplate.update(
                    "UPDATE branches SET monthly_sales_target = 400000 WHERE code = 'LIT' AND monthly_sales_target IS NULL");
            jdbcTemplate.update(
                    "UPDATE branches SET monthly_sales_target = 350000 WHERE code = 'WEB' AND monthly_sales_target IS NULL");
            log.info("Branch schema patch applied");
        } catch (Exception e) {
            log.warn("Branch schema patch skipped or partial: {}", e.getMessage());
        }
    }
}
