package com.salonplatform.copilot.gateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Creates the AI gateway's audit and usage table if it does not exist. It holds decisions and costs, never message text. */
@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class AiGatewaySchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS ai_gateway_events (
                        id          UUID PRIMARY KEY,
                        created_at  TIMESTAMPTZ NOT NULL,
                        kind        VARCHAR(16) NOT NULL,
                        channel     VARCHAR(40),
                        tenant_id   VARCHAR(64),
                        user_id     VARCHAR(64),
                        decision    VARCHAR(16),
                        reason      VARCHAR(80),
                        categories  VARCHAR(200),
                        rule_ids    VARCHAR(200),
                        provider    VARCHAR(32),
                        model       VARCHAR(100),
                        tokens_in   BIGINT NOT NULL DEFAULT 0,
                        tokens_out  BIGINT NOT NULL DEFAULT 0,
                        cost_inr    NUMERIC(14,4) NOT NULL DEFAULT 0,
                        latency_ms  BIGINT NOT NULL DEFAULT 0,
                        input_chars INTEGER NOT NULL DEFAULT 0
                    )""");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_ai_gateway_events_tenant_time ON ai_gateway_events (tenant_id, created_at)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_ai_gateway_events_user_time ON ai_gateway_events (user_id, created_at)");
            log.info("AI gateway audit table ready");
        } catch (Exception e) {
            log.warn("AI gateway schema patch skipped or partial: {}", e.getMessage());
        }
    }
}
