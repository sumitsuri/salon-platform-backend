package com.salonplatform.telemetry;

import com.salonplatform.config.ProductTelemetrySchemaPatch;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * SQL helpers for usage analytics that tolerate older {@code product_events} schemas on prod
 * (missing {@code feature_key} / {@code route} until DDL runs).
 */
@Component
@RequiredArgsConstructor
public class ProductUsageSqlSupport {

    private final JdbcTemplate jdbcTemplate;
    private final ProductTelemetrySchemaPatch schemaPatch;

    private volatile Boolean featureKeyColumnPresent;

    public void ensureTelemetrySchema() {
        schemaPatch.ensureUsageRollupTables();
        schemaPatch.ensureProductEventsColumnsPublic();
    }

    /** Expression for grouping/counting “features” in {@code product_events}. */
    public String featureDimensionSql() {
        if (Boolean.FALSE.equals(featureKeyColumnPresent)) {
            return "event_name";
        }
        if (Boolean.TRUE.equals(featureKeyColumnPresent)) {
            return "COALESCE(NULLIF(TRIM(feature_key), ''), event_name)";
        }
        synchronized (this) {
            if (featureKeyColumnPresent == null) {
                featureKeyColumnPresent = probeColumn("feature_key");
            }
            return featureKeyColumnPresent
                    ? "COALESCE(NULLIF(TRIM(feature_key), ''), event_name)"
                    : "event_name";
        }
    }

    private boolean probeColumn(String column) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*) FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = 'product_events'
                              AND column_name = ?
                            """,
                    Integer.class,
                    column);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
