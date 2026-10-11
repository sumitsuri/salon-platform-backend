package com.salonplatform.telemetry;

import com.salonplatform.config.ProductTelemetryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductUsageRollupService {

    private final ProductTelemetryProperties properties;
    private final JdbcTemplate jdbcTemplate;

    public void rollupRecentDays(int daysBack) {
        if (!properties.isEnabled()) {
            return;
        }
        LocalDate end = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        LocalDate start = end.minusDays(Math.max(1, daysBack));
        for (LocalDate day = start; day.isBefore(end); day = day.plusDays(1)) {
            rollupDay(day);
        }
    }

    public void rollupDay(LocalDate usageDate) {
        Instant from = usageDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = usageDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Date sqlDate = Date.valueOf(usageDate);

        jdbcTemplate.update("""
                        DELETE FROM usage_daily_tenant WHERE usage_date = ?
                        """, sqlDate);
        jdbcTemplate.update("""
                        INSERT INTO usage_daily_tenant (
                            tenant_id, usage_date, active_users, active_staff, active_managers, session_count
                        )
                        SELECT
                            tenant_id,
                            ?::date,
                            COUNT(DISTINCT user_id),
                            COUNT(DISTINCT user_id) FILTER (WHERE role = 'SALON_STAFF'),
                            COUNT(DISTINCT user_id) FILTER (WHERE role IN ('BRANCH_MANAGER', 'SALON_MANAGER', 'BRAND_ADMIN')),
                            COUNT(DISTINCT session_id) FILTER (WHERE session_id IS NOT NULL)
                        FROM product_events
                        WHERE tenant_id IS NOT NULL
                          AND occurred_at >= ? AND occurred_at < ?
                        GROUP BY tenant_id
                        """, sqlDate, from, to);

        jdbcTemplate.update("DELETE FROM usage_daily_tenant_feature WHERE usage_date = ?", sqlDate);
        jdbcTemplate.update("""
                        INSERT INTO usage_daily_tenant_feature (tenant_id, usage_date, feature_key, hit_count)
                        SELECT tenant_id, ?::date, feature_key, COUNT(*)
                        FROM product_events
                        WHERE tenant_id IS NOT NULL
                          AND feature_key IS NOT NULL
                          AND occurred_at >= ? AND occurred_at < ?
                        GROUP BY tenant_id, feature_key
                        """, sqlDate, from, to);

        jdbcTemplate.update("DELETE FROM usage_daily_user WHERE usage_date = ?", sqlDate);
        jdbcTemplate.update("""
                        INSERT INTO usage_daily_user (
                            tenant_id, user_id, usage_date, event_count, distinct_features, last_event_at
                        )
                        SELECT
                            tenant_id,
                            user_id,
                            ?::date,
                            COUNT(*),
                            COUNT(DISTINCT feature_key) FILTER (WHERE feature_key IS NOT NULL),
                            MAX(occurred_at)
                        FROM product_events
                        WHERE tenant_id IS NOT NULL
                          AND user_id IS NOT NULL
                          AND occurred_at >= ? AND occurred_at < ?
                        GROUP BY tenant_id, user_id
                        """, sqlDate, from, to);

        jdbcTemplate.update("""
                        UPDATE user_usage_snapshot s
                        SET days_active_28d = sub.days_active
                        FROM (
                            SELECT tenant_id, user_id, COUNT(*) AS days_active
                            FROM usage_daily_user
                            WHERE usage_date >= (?::date - INTERVAL '27 days')
                              AND usage_date <= ?::date
                              AND event_count > 0
                            GROUP BY tenant_id, user_id
                        ) sub
                        WHERE s.tenant_id = sub.tenant_id AND s.user_id = sub.user_id
                        """, sqlDate, sqlDate);
    }

    public void purgeOldRawEvents() {
        if (!properties.isEnabled() || properties.getRetentionDays() <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minusSeconds(properties.getRetentionDays() * 86_400L);
        int deleted = jdbcTemplate.update(
                "DELETE FROM product_events WHERE received_at < ?",
                cutoff);
        if (deleted > 0) {
            log.info("Purged {} product_events older than {} days", deleted, properties.getRetentionDays());
        }
    }
}
