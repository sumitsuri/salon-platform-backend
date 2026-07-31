package com.salonplatform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class ReviewsSchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS review_invitations (
                        id UUID PRIMARY KEY,
                        tenant_id UUID NOT NULL,
                        branch_id UUID NOT NULL,
                        branch_name VARCHAR(255) NOT NULL,
                        visit_id UUID NOT NULL UNIQUE,
                        invoice_id UUID,
                        customer_id UUID,
                        customer_first_name VARCHAR(128),
                        google_review_url VARCHAR(512),
                        status VARCHAR(32) NOT NULL,
                        expires_at TIMESTAMP NOT NULL,
                        submitted_at TIMESTAMP,
                        created_at TIMESTAMP
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS reviews (
                        id UUID PRIMARY KEY,
                        invitation_id UUID NOT NULL UNIQUE,
                        tenant_id UUID NOT NULL,
                        branch_id UUID NOT NULL,
                        visit_id UUID NOT NULL UNIQUE,
                        overall_rating INT NOT NULL,
                        improvement_tags VARCHAR(512),
                        comment VARCHAR(2000),
                        google_review_redirected BOOLEAN DEFAULT FALSE,
                        submitted_at TIMESTAMP
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS review_recoveries (
                        id UUID PRIMARY KEY,
                        review_id UUID NOT NULL UNIQUE,
                        tenant_id UUID NOT NULL,
                        branch_id UUID NOT NULL,
                        visit_id UUID NOT NULL,
                        overall_rating INT NOT NULL,
                        status VARCHAR(32) NOT NULL,
                        notes VARCHAR(2000),
                        created_at TIMESTAMP,
                        updated_at TIMESTAMP,
                        resolved_at TIMESTAMP
                    )
                    """);
            log.info("Reviews schema patch applied");
        } catch (Exception e) {
            log.warn("Reviews schema patch skipped or partial: {}", e.getMessage());
        }
    }
}
