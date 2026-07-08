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
public class ExpenditureSchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS branch_expenditures (
                        id UUID PRIMARY KEY,
                        tenant_id UUID NOT NULL,
                        branch_id UUID NOT NULL,
                        category VARCHAR(64) NOT NULL,
                        expense_month DATE NOT NULL,
                        amount NUMERIC(19, 2) NOT NULL,
                        description VARCHAR(512),
                        active BOOLEAN DEFAULT TRUE,
                        created_at TIMESTAMP,
                        updated_at TIMESTAMP
                    )
                    """);
            log.info("Expenditure schema patch applied");
        } catch (Exception e) {
            log.warn("Expenditure schema patch skipped or partial: {}", e.getMessage());
        }
    }
}
