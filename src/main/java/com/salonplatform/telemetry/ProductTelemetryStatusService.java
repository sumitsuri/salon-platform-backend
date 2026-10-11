package com.salonplatform.telemetry;

import com.salonplatform.config.ProductTelemetryProperties;
import com.salonplatform.dto.platform.PlatformTelemetryStatusResponse;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class ProductTelemetryStatusService {

    private final ProductTelemetryProperties properties;
    private final ProductTelemetryReadiness readiness;
    private final JdbcTemplate jdbcTemplate;

    public PlatformTelemetryStatusResponse status() {
        SecurityUtils.assertPlatformAdmin();
        boolean tableReady = readiness.isEventsTableReady();
        long events24h = 0;
        long rollup24h = 0;
        if (tableReady) {
            Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
            try {
                Long count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM product_events WHERE occurred_at >= ?",
                        Long.class,
                        Timestamp.from(since));
                events24h = count != null ? count : 0;
            } catch (DataAccessException ignored) {
                tableReady = false;
            }
        }
        try {
            LocalDate sinceDate = LocalDate.now().minusDays(1);
            Long rollup = jdbcTemplate.queryForObject(
                    """
                            SELECT COALESCE(SUM(event_count), 0)
                            FROM usage_daily_user
                            WHERE usage_date >= ?
                            """,
                    Long.class,
                    java.sql.Date.valueOf(sinceDate));
            rollup24h = rollup != null ? rollup : 0;
        } catch (DataAccessException ignored) {
            // rollup tables optional for status
        }
        return PlatformTelemetryStatusResponse.builder()
                .ingestEnabled(properties.isEnabled())
                .eventsTableReady(tableReady)
                .eventsLast24Hours(events24h)
                .rollupRowsLast24Hours(rollup24h)
                .build();
    }
}
