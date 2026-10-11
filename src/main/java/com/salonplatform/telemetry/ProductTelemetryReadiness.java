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

    /** Called after schema patch creates or verifies {@code product_events}. */
    public void markEventsTablePresent() {
        eventsTableReady = true;
    }

    public void clearCache() {
        eventsTableReady = null;
    }

    public boolean isEventsTableReady() {
        if (eventsTableReady != null) {
            return eventsTableReady;
        }
        synchronized (this) {
            if (eventsTableReady != null) {
                return eventsTableReady;
            }
            eventsTableReady = probeEventsTable();
            return eventsTableReady;
        }
    }

    private boolean probeEventsTable() {
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product_events", Integer.class);
            return true;
        } catch (Exception catalogEx) {
            try {
                Integer exists = jdbcTemplate.queryForObject(
                        """
                                SELECT COUNT(*)
                                FROM information_schema.tables
                                WHERE table_schema = 'public' AND table_name = 'product_events'
                                """,
                        Integer.class);
                boolean ready = exists != null && exists > 0;
                if (!ready) {
                    log.warn("product_events table not found — portfolio uses rollup metrics until created");
                }
                return ready;
            } catch (Exception e) {
                log.warn("Could not verify product_events table: {}", e.getMessage());
                return false;
            }
        }
    }
}
