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
import java.time.ZoneOffset;
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
        long tenantEvents24h = 0;
        String latestEventAt = null;
        if (tableReady) {
            Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
            try {
                Long count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM product_events WHERE occurred_at >= ?",
                        Long.class,
                        Timestamp.from(since));
                events24h = count != null ? count : 0;
                Long withTenant = jdbcTemplate.queryForObject(
                        """
                                SELECT COUNT(*) FROM product_events
                                WHERE occurred_at >= ? AND tenant_id IS NOT NULL
                                """,
                        Long.class,
                        Timestamp.from(since));
                tenantEvents24h = withTenant != null ? withTenant : 0;
                Timestamp latest = jdbcTemplate.queryForObject(
                        "SELECT MAX(occurred_at) FROM product_events",
                        Timestamp.class);
                if (latest != null) {
                    latestEventAt = latest.toInstant().toString();
                }
            } catch (DataAccessException ignored) {
                tableReady = false;
            }
        }
        long rollup24h = 0;
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
        long eventsAllTime = 0;
        long eventsInPortfolioWindow = 0;
        if (tableReady) {
            try {
                Long all = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product_events", Long.class);
                eventsAllTime = all != null ? all : 0;
                Instant windowFrom = Instant.now().minus(60, ChronoUnit.DAYS);
                Instant windowTo = Instant.now();
                LocalDate fromDate = windowFrom.atZone(ZoneOffset.UTC).toLocalDate();
                LocalDate toDate = windowTo.atZone(ZoneOffset.UTC).toLocalDate();
                Instant rangeFrom = fromDate.atStartOfDay(ZoneOffset.UTC).toInstant();
                Instant rangeToExclusive = toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                Long inWindow = jdbcTemplate.queryForObject(
                        """
                                SELECT COUNT(*) FROM product_events
                                WHERE occurred_at >= ? AND occurred_at < ?
                                """,
                        Long.class,
                        Timestamp.from(rangeFrom),
                        Timestamp.from(rangeToExclusive));
                eventsInPortfolioWindow = inWindow != null ? inWindow : 0;
            } catch (DataAccessException ignored) {
                // diagnostic fields optional
            }
        }
        return PlatformTelemetryStatusResponse.builder()
                .ingestEnabled(properties.isEnabled())
                .eventsTableReady(tableReady)
                .eventsLast24Hours(events24h)
                .eventsWithTenantLast24Hours(tenantEvents24h)
                .latestEventAt(latestEventAt)
                .rollupRowsLast24Hours(rollup24h)
                .eventsAllTime(eventsAllTime)
                .eventsInPortfolioWindow(eventsInPortfolioWindow)
                .build();
    }
}
