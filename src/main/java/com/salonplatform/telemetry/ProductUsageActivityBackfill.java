package com.salonplatform.telemetry;

import com.salonplatform.config.ProductTelemetryProperties;
import com.salonplatform.config.ProductTelemetrySchemaPatch;
import com.salonplatform.domain.enums.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Customer usage used to start the day telemetry shipped, so a brand that had been billing for a month
 * still looked idle. This copies recent real work (payments, visits, attendance, stock, memberships,
 * packages) into {@code product_events} under the same feature keys live traffic now uses.
 * Rows are keyed by a stable {@code client_event_id}, so startup can run this every time.
 */
@Component
@Order(18)
@RequiredArgsConstructor
@Slf4j
public class ProductUsageActivityBackfill implements ApplicationRunner {

    private static final String INSERT_SQL = """
            INSERT INTO product_events (
                id, occurred_at, event_name, event_version,
                tenant_id, branch_id, user_id, role,
                portal, session_id, feature_key, route,
                properties, client, app_version, client_event_id
            ) VALUES (?, ?, 'feature_used', 1, ?, ?, ?, ?, ?, NULL, ?, ?, ?::jsonb, 'backfill', NULL, ?)
            ON CONFLICT (user_id, client_event_id) WHERE client_event_id IS NOT NULL DO NOTHING
            """;

    private static final String CUSTOMER_ROLES =
            "('BRAND_ADMIN','BRANCH_MANAGER','SALON_MANAGER','SALON_STAFF')";

    private final JdbcTemplate jdbcTemplate;
    private final ProductTelemetryProperties properties;
    private final ProductTelemetrySchemaPatch schemaPatch;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            schemaPatch.ensureProductEventsColumnsPublic();
            recategorizeLogins();
            Instant since = Instant.now().minus(Math.max(properties.getRetentionDays(), 1), ChronoUnit.DAYS);
            int inserted = 0;
            inserted += backfill("payment", "billing", "/api/v1/bookings/{id}/payments", """
                    SELECT p.id, p.tenant_id, p.branch_id, p.paid_at, p.recorded_by_user_id, u.role
                    FROM payments p
                    JOIN users u ON u.id = p.recorded_by_user_id
                    WHERE p.paid_at >= ? AND u.role IN""" + " " + CUSTOMER_ROLES, since);
            inserted += backfill("visit", "walk_in", "/api/v1/bookings", """
                    SELECT b.id, b.tenant_id, b.branch_id, b.created_at, b.created_by_user_id, u.role
                    FROM bookings b
                    JOIN users u ON u.id = b.created_by_user_id
                    WHERE b.created_at >= ? AND b.created_by_user_id IS NOT NULL
                      AND u.role IN""" + " " + CUSTOMER_ROLES, since);
            inserted += backfillAttendance(since);
            inserted += backfill("stock", "inventory", "/api/v1/inventory", """
                    SELECT m.id, m.tenant_id, m.branch_id, m.created_at, m.recorded_by_user_id, u.role
                    FROM inventory_movements m
                    JOIN users u ON u.id = m.recorded_by_user_id
                    WHERE m.created_at >= ? AND m.recorded_by_user_id IS NOT NULL
                      AND u.role IN""" + " " + CUSTOMER_ROLES, since);
            inserted += backfill("membership", "memberships", "/api/v1/memberships/subscriptions", """
                    SELECT s.id, s.tenant_id, s.branch_id, s.created_at, s.sold_by_user_id, u.role
                    FROM membership_subscriptions s
                    JOIN users u ON u.id = s.sold_by_user_id
                    WHERE s.created_at >= ? AND s.sold_by_user_id IS NOT NULL
                      AND u.role IN""" + " " + CUSTOMER_ROLES, since);
            inserted += backfill("package", "packages", "/api/v1/packages", """
                    SELECT s.id, s.tenant_id, s.branch_id, s.created_at, s.sold_by_user_id, u.role
                    FROM customer_package_subscriptions s
                    JOIN users u ON u.id = s.sold_by_user_id
                    WHERE s.created_at >= ? AND s.sold_by_user_id IS NOT NULL
                      AND u.role IN""" + " " + CUSTOMER_ROLES, since);
            if (inserted > 0) {
                log.info("Backfilled {} product usage events from billing and other activity", inserted);
            }
        } catch (Exception e) {
            log.warn("Product usage activity backfill skipped: {}", e.getMessage());
        }
    }

    /** Sign-in used to be a single "Sign-in app" bucket. Put each login on the role's app. */
    private void recategorizeLogins() {
        jdbcTemplate.update("""
                UPDATE product_events e
                SET feature_key = CASE u.role
                            WHEN 'BRAND_ADMIN' THEN 'admin.login'
                            WHEN 'SALON_STAFF' THEN 'employee.login'
                            ELSE 'manager.login'
                        END,
                    portal = CASE u.role
                            WHEN 'BRAND_ADMIN' THEN 'admin'
                            WHEN 'SALON_STAFF' THEN 'employee'
                            ELSE 'manager'
                        END
                FROM users u
                WHERE e.user_id = u.id
                  AND e.event_name = 'login_succeeded'
                  AND e.feature_key = 'auth.login'
                  AND u.role IN""" + " " + CUSTOMER_ROLES);
    }

    private int backfillAttendance(Instant since) {
        Timestamp cutoff = Timestamp.from(since);
        int count = 0;
        count += backfill("attendance-in", "attendance", "/api/v1/staff-portal/attendance/punch", """
                SELECT a.id, a.tenant_id, a.branch_id, a.entry_time,
                       COALESCE(a.recorded_by_user_id, s.user_id), u.role
                FROM attendance_records a
                LEFT JOIN staff s ON s.id = a.staff_id
                JOIN users u ON u.id = COALESCE(a.recorded_by_user_id, s.user_id)
                WHERE a.entry_time >= ? AND u.role IN""" + " " + CUSTOMER_ROLES, since);
        count += backfill("attendance-out", "attendance", "/api/v1/staff-portal/attendance/punch", """
                SELECT a.id, a.tenant_id, a.branch_id, a.exit_time,
                       COALESCE(a.recorded_by_user_id, s.user_id), u.role
                FROM attendance_records a
                LEFT JOIN staff s ON s.id = a.staff_id
                JOIN users u ON u.id = COALESCE(a.recorded_by_user_id, s.user_id)
                WHERE a.exit_time >= ? AND u.role IN""" + " " + CUSTOMER_ROLES, since);
        if (count == 0) {
            log.debug("No attendance usage to backfill since {}", cutoff);
        }
        return count;
    }

    private int backfill(String source, String featureSuffix, String route, String selectSql, Instant since) {
        try {
            List<Row> rows = jdbcTemplate.query(selectSql, (rs, i) -> new Row(
                    rs.getObject(1, UUID.class),
                    rs.getObject(2, UUID.class),
                    rs.getObject(3, UUID.class),
                    rs.getTimestamp(4),
                    rs.getObject(5, UUID.class),
                    rs.getString(6)
            ), Timestamp.from(since));
            return insert(source, featureSuffix, route, rows);
        } catch (Exception e) {
            log.warn("Usage backfill for {} skipped: {}", source, e.getMessage());
            return 0;
        }
    }

    private int insert(String source, String featureSuffix, String route, List<Row> rows) {
        List<Row> ready = new ArrayList<>();
        for (Row row : rows) {
            if (row.occurredAt == null || row.userId == null || row.tenantId == null || row.role == null) {
                continue;
            }
            ready.add(row);
        }
        if (ready.isEmpty()) {
            return 0;
        }
        int[][] counts = jdbcTemplate.batchUpdate(INSERT_SQL, ready, 200, (ps, row) -> {
            UserRole role = UserRole.valueOf(row.role);
            String portal = ProductActivityCatalog.portalFor(role);
            ps.setObject(1, UUID.randomUUID());
            ps.setTimestamp(2, row.occurredAt);
            ps.setObject(3, row.tenantId);
            ps.setObject(4, row.branchId);
            ps.setObject(5, row.userId);
            ps.setString(6, role.name());
            ps.setString(7, portal);
            ps.setString(8, portal + "." + featureSuffix);
            ps.setString(9, route);
            ps.setString(10, "{\"source\":\"backfill\",\"action\":\"" + featureSuffix + "\"}");
            ps.setObject(11, ProductActivityCatalog.backfillEventId(source, row.id, null));
        });
        int inserted = 0;
        for (int[] batch : counts) {
            for (int count : batch) {
                if (count > 0) {
                    inserted += count;
                }
            }
        }
        return inserted;
    }

    private record Row(UUID id, UUID tenantId, UUID branchId, Timestamp occurredAt, UUID userId, String role) {}
}
