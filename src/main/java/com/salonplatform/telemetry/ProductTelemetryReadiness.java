package com.salonplatform.telemetry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductTelemetryReadiness {

    private final JdbcTemplate jdbcTemplate;

    private volatile Boolean eventsTableReady;

    public boolean isEventsTableReady() {
        if (eventsTableReady != null) {
            return eventsTableReady;
        }
        synchronized (this) {
            if (eventsTableReady != null) {
                return eventsTableReady;
            }
            try {
                Integer exists = jdbcTemplate.queryForObject(
                        """
                                SELECT COUNT(*)
                                FROM information_schema.tables
                                WHERE table_schema = 'public' AND table_name = 'product_events'
                                """,
                        Integer.class);
                eventsTableReady = exists != null && exists > 0;
                if (!eventsTableReady) {
                    log.warn("product_events table not found — usage analytics will return empty metrics");
                }
            } catch (Exception e) {
                log.warn("Could not verify product_events table: {}", e.getMessage());
                eventsTableReady = false;
            }
            return eventsTableReady;
        }
    }
}
