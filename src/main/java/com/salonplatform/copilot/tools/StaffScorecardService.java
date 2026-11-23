package com.salonplatform.copilot.tools;

import com.salonplatform.dto.analytics.AttendanceDashboardResponse;
import com.salonplatform.dto.analytics.StaffAttendanceSummary;
import com.salonplatform.dto.analytics.StaffSalesPerformanceResponse;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.service.AnalyticsService;
import com.salonplatform.service.AttendanceAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One row per staff member that answers "is this person earning their keep": revenue, pro-rated salary, the multiple
 * of salary achieved against a target, distinct customers served, services performed and attendance.
 *
 * <p>Revenue and service counts reuse the staff sales logic behind the Staff screens, attendance and salary reuse the
 * attendance dashboard, so figures match those screens. All arithmetic happens here, in code, never in the model.
 */
@Service
@RequiredArgsConstructor
public class StaffScorecardService {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final int TOP_SERVICES = 5;

    /** How many times a service was performed by one person. */
    record ServiceCount(String service, long count) {}

    private final AnalyticsService analytics;
    private final AttendanceAnalyticsService attendance;
    private final NamedParameterJdbcTemplate jdbc;

    public Map<String, Object> scorecard(LocalDate start, LocalDate end, List<UUID> branchIds, double targetMultiple) {
        UUID tenantId = SecurityUtils.requireTenantId();
        List<StaffSalesPerformanceResponse.StaffSalesRow> sales =
                analytics.getStaffSalesPerformance(start, end, branchIds).getStaff();
        AttendanceDashboardResponse att = attendance.getDashboard(start, end, branchIds);
        Map<UUID, Long> customers = new HashMap<>();
        Map<UUID, List<ServiceCount>> services = new HashMap<>();
        loadCustomersAndServices(tenantId, start, end, branchIds, customers, services);
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        return assemble(sales, att.getStaffSummaries(), customers, services, targetMultiple, start, end, days);
    }

    /** Pure assembly of the pieces into the scorecard, separated out so the arithmetic can be tested alone. */
    static Map<String, Object> assemble(
            List<StaffSalesPerformanceResponse.StaffSalesRow> sales,
            List<StaffAttendanceSummary> attendance,
            Map<UUID, Long> customers,
            Map<UUID, List<ServiceCount>> services,
            double targetMultiple,
            LocalDate start,
            LocalDate end,
            long periodDays) {

        Map<String, StaffSalesPerformanceResponse.StaffSalesRow> salesById = new LinkedHashMap<>();
        for (StaffSalesPerformanceResponse.StaffSalesRow r : sales) {
            salesById.put(String.valueOf(r.getStaffId()), r);
        }
        Map<String, StaffAttendanceSummary> attById = new LinkedHashMap<>();
        for (StaffAttendanceSummary a : attendance) {
            attById.put(a.getStaffId(), a);
        }
        List<String> ids = new ArrayList<>(salesById.keySet());
        attById.keySet().stream().filter(k -> !salesById.containsKey(k)).forEach(ids::add);

        List<Map<String, Object>> rows = new ArrayList<>();
        double totalRevenue = 0;
        for (String id : ids) {
            StaffSalesPerformanceResponse.StaffSalesRow s = salesById.get(id);
            StaffAttendanceSummary a = attById.get(id);
            UUID uuid = tryUuid(id);
            double revenue = s == null ? 0 : money(s.getFinalRevenue());
            double salary = a == null ? 0 : money(a.getProRatedSalary());
            totalRevenue += revenue;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", s != null ? s.getStaffName() : a.getStaffName());
            row.put("branch", s != null ? s.getBranchName() : a.getBranchName());
            row.put("revenue", Math.round(revenue));
            row.put("serviceLines", s == null ? 0 : s.getSalesCount());
            row.put("avgTicket", s == null ? 0 : Math.round(money(s.getAvgFinalTicket())));
            row.put("uniqueCustomers", uuid == null ? 0 : customers.getOrDefault(uuid, 0L));
            List<ServiceCount> svc = uuid == null ? List.of() : services.getOrDefault(uuid, List.of());
            row.put("distinctServices", svc.size());
            row.put("topServices", svc.stream().limit(TOP_SERVICES).map(x -> x.service() + " x" + x.count()).toList());
            row.put("daysPresent", a == null ? null : a.getDaysPresent());
            row.put("daysOnLeave", a == null ? null : a.getDaysLeave());
            row.put("lateArrivals", a == null ? null : a.getLateArrivals());
            row.put("outsideBranchCheckIns", a == null ? null : a.getGeoFlags());
            row.put("salaryForPeriod", a == null ? null : Math.round(salary));
            if (salary > 0) {
                double target = salary * targetMultiple;
                row.put("targetRevenue", Math.round(target));
                row.put("multipleOfSalary", Math.round(revenue / salary * 10) / 10.0);
                row.put("gapToTarget", Math.round(target - revenue)); // positive = still short of the target
                row.put("meetsTarget", revenue >= target);
            } else {
                row.put("targetRevenue", null);
                row.put("multipleOfSalary", null);
                row.put("gapToTarget", null);
                row.put("meetsTarget", null);
            }
            rows.add(row);
        }
        rows.sort(Comparator.comparingLong((Map<String, Object> r) -> ((Number) r.get("revenue")).longValue()).reversed());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", start + " to " + end + " (" + periodDays + " days)");
        out.put("targetMultiple", targetMultiple);
        out.put("staffCount", rows.size());
        out.put("totalRevenue", Math.round(totalRevenue));
        out.put("staff", rows);
        out.put("howToRead", "salaryForPeriod is the monthly salary pro-rated to the period. multipleOfSalary = revenue / "
                + "salaryForPeriod. gapToTarget = targetRevenue - revenue (positive means still short). uniqueCustomers counts "
                + "distinct customer records billed by that person; guests without a customer record are not counted. "
                + "Staff with no salary on file have no target figures.");
        return out;
    }

    private void loadCustomersAndServices(UUID tenantId, LocalDate start, LocalDate end, List<UUID> branchIds,
                                          Map<UUID, Long> customers, Map<UUID, List<ServiceCount>> services) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenant", tenantId)
                .addValue("from", Timestamp.from(start.atStartOfDay(IST).toInstant()))
                .addValue("to", Timestamp.from(end.plusDays(1).atStartOfDay(IST).toInstant()));
        String branchFilter = "";
        if (branchIds != null && !branchIds.isEmpty()) {
            params.addValue("branches", branchIds);
            branchFilter = " AND i.branch_id IN (:branches)";
        }
        String from = """
                FROM invoices i
                JOIN bookings b ON b.id = i.booking_id
                JOIN booking_line_items li ON li.booking_id = b.id
                WHERE i.tenant_id = :tenant AND i.deleted_at IS NULL
                  AND i.issued_at >= :from AND i.issued_at < :to
                  AND li.staff_id IS NOT NULL""" + branchFilter;

        jdbc.query("SELECT li.staff_id, COUNT(DISTINCT b.customer_id) AS customers " + from + " GROUP BY li.staff_id",
                params, rs -> {
                    customers.put(rs.getObject("staff_id", UUID.class), rs.getLong("customers"));
                });
        jdbc.query("SELECT li.staff_id, li.service_name, SUM(COALESCE(li.quantity, 1)) AS n " + from
                        + " GROUP BY li.staff_id, li.service_name ORDER BY n DESC",
                params, rs -> {
                    services.computeIfAbsent(rs.getObject("staff_id", UUID.class), k -> new ArrayList<>())
                            .add(new ServiceCount(rs.getString("service_name"), rs.getLong("n")));
                });
    }

    private static double money(BigDecimal v) {
        return v == null ? 0 : v.doubleValue();
    }

    private static UUID tryUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (Exception e) {
            return null;
        }
    }
}
