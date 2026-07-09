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
public class InventorySchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS vendors (
                        id UUID PRIMARY KEY,
                        tenant_id UUID NOT NULL,
                        name VARCHAR(255) NOT NULL,
                        contact_phone VARCHAR(32),
                        contact_email VARCHAR(255),
                        notes VARCHAR(512),
                        active BOOLEAN DEFAULT TRUE,
                        created_at TIMESTAMP,
                        updated_at TIMESTAMP
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS inventory_products (
                        id UUID PRIMARY KEY,
                        tenant_id UUID NOT NULL,
                        vendor_id UUID NOT NULL,
                        name VARCHAR(255) NOT NULL,
                        sku VARCHAR(64),
                        category VARCHAR(32) NOT NULL,
                        unit VARCHAR(16) NOT NULL,
                        unit_cost NUMERIC(19, 2) NOT NULL,
                        retail_price NUMERIC(19, 2),
                        reorder_level NUMERIC(19, 3),
                        active BOOLEAN DEFAULT TRUE,
                        created_at TIMESTAMP,
                        updated_at TIMESTAMP
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS branch_inventory (
                        id UUID PRIMARY KEY,
                        tenant_id UUID NOT NULL,
                        branch_id UUID NOT NULL,
                        product_id UUID NOT NULL,
                        quantity NUMERIC(19, 3) NOT NULL DEFAULT 0,
                        updated_at TIMESTAMP,
                        UNIQUE(branch_id, product_id)
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS inventory_movements (
                        id UUID PRIMARY KEY,
                        tenant_id UUID NOT NULL,
                        branch_id UUID NOT NULL,
                        product_id UUID NOT NULL,
                        movement_type VARCHAR(32) NOT NULL,
                        quantity NUMERIC(19, 3) NOT NULL,
                        unit_cost NUMERIC(19, 2) NOT NULL,
                        total_cost NUMERIC(19, 2) NOT NULL,
                        movement_date DATE NOT NULL,
                        note VARCHAR(512),
                        recorded_by_user_id UUID,
                        created_at TIMESTAMP
                    )
                    """);
            log.info("Inventory schema patch applied");
        } catch (Exception e) {
            log.warn("Inventory schema patch skipped or partial: {}", e.getMessage());
        }
    }
}
