package com.salonplatform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Composite indexes backing the CEO/admin dashboard's date-range analytics queries
 * (invoices, booking line items, payments, attendance, leave, expenditure, inventory
 * movements). None of these tables had indexes matching their actual WHERE/JOIN
 * shape, so widening a date range forced sequential scans that grew with table size.
 *
 * Uses CONCURRENTLY so index builds don't hold a write lock on these tables in
 * production — each statement runs on its own auto-committed connection (required by
 * Postgres for CONCURRENTLY) and is guarded individually so one failure doesn't skip
 * the rest.
 */
@Component
@Order(4)
@RequiredArgsConstructor
@Slf4j
public class AnalyticsPerformanceSchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        // invoices: every dashboard/P&L/staff/branch query filters tenant + deleted_at + issued_at range.
        createIndex("idx_invoices_tenant_deleted_issued", "invoices (tenant_id, deleted_at, issued_at)");

        // booking_line_items: looked up by booking_id from every invoice in range, repeatedly, with no index at all.
        createIndex("idx_booking_line_items_booking", "booking_line_items (booking_id)");

        // payments: dashboard payment-mode breakdown needs tenant + date range (and branch) scoped lookups.
        createIndex("idx_payments_tenant_paid_at", "payments (tenant_id, paid_at)");
        createIndex("idx_payments_tenant_branch_paid_at", "payments (tenant_id, branch_id, paid_at)");

        // payment_splits: looked up per SPLIT payment by payment_id, unindexed.
        createIndex("idx_payment_splits_payment", "payment_splits (payment_id)");

        // attendance_records: existing idx_attendance_staff_date (staff_id, work_date) doesn't match the
        // dashboard's tenant[+branch]+work_date-range query shape, so it can't be used for this lookup.
        createIndex("idx_attendance_tenant_branch_work_date", "attendance_records (tenant_id, branch_id, work_date)");

        // leave_records: unindexed; used for tenant[+branch]-scoped date-range overlap checks.
        createIndex("idx_leave_records_tenant_branch_dates", "leave_records (tenant_id, branch_id, start_date, end_date)");

        // branch_expenditures: P&L query filters tenant + expense_month range.
        createIndex("idx_branch_expenditures_tenant_month", "branch_expenditures (tenant_id, expense_month)");

        // inventory_movements: overview query filters tenant + movement_date range.
        createIndex("idx_inventory_movements_tenant_date", "inventory_movements (tenant_id, movement_date)");

        log.info("Analytics performance schema patch finished");
    }

    private void createIndex(String indexName, String onClause) {
        try {
            jdbcTemplate.execute("CREATE INDEX CONCURRENTLY IF NOT EXISTS " + indexName + " ON " + onClause);
        } catch (Exception e) {
            log.warn("Skipping index {}: {}", indexName, e.getMessage());
        }
    }
}
