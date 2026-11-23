package com.salonplatform.copilot.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.enums.BranchStatus;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.dto.analytics.DashboardResponse;
import com.salonplatform.dto.demand.BranchDemand;
import com.salonplatform.dto.demand.DemandInsightsResponse;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.service.AnalyticsService;
import com.salonplatform.service.AttendanceAnalyticsService;
import com.salonplatform.service.DemandInsightsService;
import com.salonplatform.service.InventoryAnalyticsService;
import com.salonplatform.service.PlAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The assistant's tools: thin, read-only wrappers over the analytics the app already computes. */
@Component
@RequiredArgsConstructor
public class CopilotToolbox {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 400;

    private static final String RANGE_PROPS = """
            "startDate":{"type":"string","description":"First day, YYYY-MM-DD. Default: 29 days before endDate."},
            "endDate":{"type":"string","description":"Last day, YYYY-MM-DD. Default: today."}""";
    private static final String BRANCH_PROP = """
            "branchIds":{"type":"array","items":{"type":"string"},"description":"Branch ids from list_branches. Omit for all branches."}""";

    private final AnalyticsService analytics;
    private final AttendanceAnalyticsService attendance;
    private final InventoryAnalyticsService inventory;
    private final PlAnalyticsService pl;
    private final StaffScorecardService staffScorecard;
    private final DemandInsightsService demand;
    private final com.salonplatform.forecast.SalesForecastService salesForecast;
    private final com.salonplatform.forecast.StockReadinessService stockReadiness;
    private final BranchRepository branches;
    private final ObjectMapper mapper;

    public List<CopilotTool> tools() {
        List<CopilotTool> t = new ArrayList<>();

        t.add(new CopilotTool("list_branches", "Looking up your branches",
                "List the active branches with their ids and opening hours. Call this first to turn a branch name into an id.",
                "{\"type\":\"object\",\"properties\":{}}",
                args -> branches.findByTenantIdAndStatus(SecurityUtils.requireTenantId(), BranchStatus.ACTIVE).stream()
                        .map(CopilotToolbox::branchRow).toList()));

        t.add(new CopilotTool("sales_overview", "Checking sales",
                "Revenue, visits, average ticket, discounts, per-branch results, top services and top staff for a period.",
                schema(RANGE_PROPS + "," + BRANCH_PROP),
                args -> {
                    LocalDate[] r = range(args);
                    return dashboardWithoutDailySeries(analytics.getDashboard(r[0], r[1], branchIds(args)));
                }));

        t.add(new CopilotTool("compare_periods", "Comparing two periods",
                "Compare revenue, visits and average ticket between an earlier and a later period, per branch, with the "
                        + "change split into a visits effect and a ticket effect. Use it to find why sales went up or down.",
                schema("\"startA\":{\"type\":\"string\"},\"endA\":{\"type\":\"string\"},\"startB\":{\"type\":\"string\"},"
                        + "\"endB\":{\"type\":\"string\"}," + BRANCH_PROP, "startA", "endA", "startB", "endB"),
                args -> {
                    LocalDate sa = date(args, "startA"), ea = date(args, "endA");
                    LocalDate sb = date(args, "startB"), eb = date(args, "endB");
                    checkRange(sa, ea);
                    checkRange(sb, eb);
                    List<UUID> ids = branchIds(args);
                    return PeriodComparison.compare(analytics.getDashboard(sa, ea, ids), analytics.getDashboard(sb, eb, ids));
                }));

        t.add(new CopilotTool("service_mix", "Checking services",
                "Which services earn the most in a period: revenue, share and counts.",
                schema(RANGE_PROPS + "," + BRANCH_PROP),
                args -> {
                    LocalDate[] r = range(args);
                    return analytics.getServiceContribution(r[0], r[1], branchIds(args), null, 0, 15);
                }));

        t.add(new CopilotTool("staff_sales", "Checking staff sales",
                "Sales per staff member for a period: revenue, service lines and average ticket. For revenue against salary, "
                        + "unique customers, services performed or attendance per person, use staff_scorecard instead.",
                schema(RANGE_PROPS + "," + BRANCH_PROP),
                args -> {
                    LocalDate[] r = range(args);
                    return analytics.getStaffSalesPerformance(r[0], r[1], branchIds(args));
                }));

        t.add(new CopilotTool("staff_scorecard", "Building the staff scorecard",
                "One row per staff member: revenue, salary for the period, multiple of salary achieved against a target "
                        + "(default 4x) and the gap, unique customers, services performed, days present, late arrivals. "
                        + "Use it for any question about individual staff performance or whether people earn their salary.",
                schema(RANGE_PROPS + "," + BRANCH_PROP
                        + ",\"targetMultiple\":{\"type\":\"number\",\"description\":\"Target revenue as a multiple of salary. Default 4.\"}"),
                args -> {
                    LocalDate[] r = range(args);
                    double multiple = args.hasNonNull("targetMultiple") ? args.get("targetMultiple").asDouble() : 4.0;
                    if (multiple <= 0 || multiple > 50) {
                        throw new IllegalArgumentException("targetMultiple must be between 0 and 50.");
                    }
                    return staffScorecard.scorecard(r[0], r[1], branchIds(args), multiple);
                }));

        t.add(new CopilotTool("attendance_overview", "Checking attendance",
                "Staff attendance for a period: days present and on leave, late arrivals, early exits and check-ins outside "
                        + "the branch, per person, with today's headcount.",
                schema(RANGE_PROPS + "," + BRANCH_PROP),
                args -> {
                    LocalDate[] r = range(args);
                    return attendanceSummary(attendance.getDashboard(r[0], r[1], branchIds(args)));
                }));

        t.add(new CopilotTool("busy_hours", "Checking busy hours",
                "When customers arrive at each branch by weekday and hour, how stretched or idle the team is, and the "
                        + "recommended actions (extra cover in rush hours, using quiet hours, moving people).",
                schema(RANGE_PROPS + ",\"branchId\":{\"type\":\"string\",\"description\":\"One branch id; omit for all.\"}"),
                args -> {
                    LocalDate[] r = range(args);
                    UUID branchId = args.hasNonNull("branchId") ? uuid(args.get("branchId").asText()) : null;
                    return busyHoursSummary(demand.getInsights(r[0], r[1], branchId));
                }));

        t.add(new CopilotTool("inventory_overview", "Checking stock",
                "Stock for a month: spend, usage and products at or below their reorder level.",
                schema("\"month\":{\"type\":\"string\",\"description\":\"Any date in the month, YYYY-MM-DD. Default: this month.\"},"
                        + BRANCH_PROP),
                args -> {
                    LocalDate month = args.hasNonNull("month") ? date(args, "month") : LocalDate.now(IST).withDayOfMonth(1);
                    return inventory.getOverview(month, branchIds(args));
                }));

        t.add(new CopilotTool("sales_forecast", "Forecasting sales",
                "Expected sales for the brand and each branch for the coming days (default 30, up to 90), with a likely range, how the month "
                        + "is pacing against its target, festival effects, and how accurate the forecast has been on past periods. Use it for any "
                        + "question about future or expected sales.",
                schema("\"days\":{\"type\":\"integer\",\"description\":\"How many days ahead, 7 to 90. Default 30.\"}"),
                args -> forecastSummary(salesForecast.forecast(args.hasNonNull("days") ? args.get("days").asInt() : 30))));

        t.add(new CopilotTool("stock_readiness", "Checking stock for upcoming services",
                "Whether each branch has enough of each product for the services already booked plus the walk-ins the forecast expects, "
                        + "over the next few days (default 7, up to 30): products that may run out, products running low, and how much to order.",
                schema("\"days\":{\"type\":\"integer\",\"description\":\"How many days ahead, 1 to 30. Default 7.\"}"),
                args -> stockSummary(stockReadiness.readiness(args.hasNonNull("days") ? args.get("days").asInt() : 7))));

        t.add(new CopilotTool("profit_and_loss", "Checking profit and loss",
                "Revenue, expenses and profit for a period.",
                schema(RANGE_PROPS + "," + BRANCH_PROP),
                args -> {
                    LocalDate[] r = range(args);
                    return pl.getPlSummary(r[0], r[1], branchIds(args));
                }));

        return t;
    }

    // ---- shaping results for the model ----

    private static Map<String, Object> forecastScope(com.salonplatform.forecast.SalesForecastService.Scope sc, boolean withWeeks) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", sc.name());
        if (!sc.forecastable() || sc.totals() == null) {
            m.put("forecastable", false);
            m.put("note", sc.note());
            return m;
        }
        var q = sc.quality();
        m.put("confidence", q.confidence());
        if (q.backtests() > 0) m.put("typicalMonthlyErrorPercent", Math.round(q.monthError() * 100));
        var tt = sc.totals();
        m.put("next7Days", Map.of("expected", Math.round(tt.next7()), "low", Math.round(tt.next7Low()), "high", Math.round(tt.next7High())));
        m.put("next30Days", Map.of("expected", Math.round(tt.next30()), "low", Math.round(tt.next30Low()), "high", Math.round(tt.next30High())));
        var p = sc.pacing();
        if (p != null) {
            Map<String, Object> pace = new LinkedHashMap<>();
            pace.put("monthToDate", Math.round(p.monthToDate()));
            pace.put("expectedMonthEnd", Math.round(p.expectedMonthEnd()));
            if (p.target() != null) {
                pace.put("monthlyTarget", Math.round(p.target()));
                pace.put("expectedVersusTargetPercent", Math.round(p.expectedVsTarget() * 100));
            }
            m.put("thisMonth", pace);
        }
        if (withWeeks) {
            List<Map<String, Object>> weeks = new ArrayList<>();
            double sum = 0;
            LocalDate start = null;
            for (var d : sc.days()) {
                if (start == null) start = d.date();
                sum += d.expected();
                if (d.date().getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                    weeks.add(Map.of("from", start.toString(), "to", d.date().toString(), "expected", Math.round(sum)));
                    sum = 0;
                    start = null;
                }
            }
            if (start != null) weeks.add(Map.of("from", start.toString(), "to", sc.days().get(sc.days().size() - 1).date().toString(), "expected", Math.round(sum)));
            m.put("weeks", weeks);
        }
        return m;
    }

    private static Object forecastSummary(com.salonplatform.forecast.SalesForecastService.ForecastResponse r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("trainedThrough", r.trainedThrough());
        out.put("horizonDays", r.horizonDays());
        if (r.stale()) out.put("warning", "The latest sales are from " + r.trainedThrough() + "; the forecast is built from that.");
        if (r.brand() != null) out.put("brand", forecastScope(r.brand(), true));
        out.put("branches", r.branches().stream().map(b -> forecastScope(b, false)).toList());
        List<Map<String, Object>> fest = new ArrayList<>();
        for (var f : r.festivals()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("festival", f.name());
            m.put("date", f.date());
            m.put("expectedImpact", Math.round(f.impact()));
            m.put("expectedImpactPercent", Math.round(f.percent() * 100));
            m.put("basis", f.learned() ? "learned from this brand's own sales" : "starting assumption, not yet confirmed by this brand's sales");
            fest.add(m);
        }
        out.put("festivals", fest);
        return out;
    }

    private static Object stockSummary(com.salonplatform.forecast.StockReadinessService.Readiness r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", r.from());
        out.put("to", r.to());
        out.put("usageHistoryAvailable", r.hasUsageHistory());
        List<Map<String, Object>> list = new ArrayList<>();
        for (var b : r.branches()) {
            Map<String, Object> bm = new LinkedHashMap<>();
            bm.put("branch", b.name());
            bm.put("bookedServices", b.bookedServices());
            bm.put("mayRunOut", b.shortCount());
            bm.put("runningLow", b.lowCount());
            List<Map<String, Object>> items = new ArrayList<>();
            for (var i : b.items().stream().filter(x -> x.status() == com.salonplatform.forecast.StockReadinessService.Status.SHORT
                    || x.status() == com.salonplatform.forecast.StockReadinessService.Status.LOW).limit(10).toList()) {
                Map<String, Object> im = new LinkedHashMap<>();
                im.put("product", i.name());
                im.put("status", i.status());
                im.put("unit", i.unit());
                im.put("inStock", Math.round(i.onHand()));
                im.put("neededForBookedServices", Math.round(i.bookedNeed()));
                im.put("expectedNeed", Math.round(i.expectedNeed()));
                if (i.runsOutOn() != null) im.put("runsOutOn", i.runsOutOn());
                im.put("suggestedOrder", Math.round(i.suggestedOrder()));
                im.put("confidence", i.confidence());
                items.add(im);
            }
            bm.put("atRisk", items);
            list.add(bm);
        }
        out.put("branches", list);
        return out;
    }

    private static Map<String, Object> branchRow(Branch b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("name", b.getName());
        m.put("code", b.getCode());
        m.put("opensAt", b.getOpenTime());
        m.put("closesAt", b.getCloseTime());
        return m;
    }

    private Object dashboardWithoutDailySeries(DashboardResponse d) {
        ObjectNode node = mapper.valueToTree(d);
        node.remove("branchTrends");
        return node;
    }

    /** Per-person attendance without the raw punch records and leave list, which are large and not needed to answer. */
    private Object attendanceSummary(com.salonplatform.dto.analytics.AttendanceDashboardResponse d) {
        List<Map<String, Object>> staff = new ArrayList<>();
        for (var s : d.getStaffSummaries()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.getStaffName());
            m.put("branch", s.getBranchName());
            m.put("daysPresent", s.getDaysPresent());
            m.put("daysOnLeave", s.getDaysLeave());
            m.put("lateArrivals", s.getLateArrivals());
            m.put("earlyExits", s.getEarlyExits());
            m.put("outsideBranchCheckIns", s.getGeoFlags());
            m.put("avgHoursPerDay", s.getAvgHoursPerDay());
            staff.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalStaff", d.getTotalStaff());
        out.put("presentToday", d.getPresentToday());
        out.put("onLeaveToday", d.getOnLeaveToday());
        out.put("absentToday", d.getAbsentToday());
        out.put("staff", staff);
        return out;
    }

    /** The conclusions of the Busy hours screen, without the hour-by-hour grids. */
    private Object busyHoursSummary(DemandInsightsResponse r) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (BranchDemand b : r.getBranches()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("branch", b.getBranchName());
            m.put("opensAt", b.getOpenTime());
            m.put("closesAt", b.getCloseTime());
            m.put("visits", b.getTotalVisits());
            m.put("cancelled", b.getTotalCancelled());
            m.put("revenue", Math.round(b.getTotalRevenue()));
            m.put("enoughData", b.isEnoughData());
            m.put("dataNote", b.getDataNote());
            m.put("summary", b.getSummary());
            m.put("recommendations", b.getRecommendations());
            out.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", r.getStartDate() + " to " + r.getEndDate());
        result.put("branches", out);
        return result;
    }

    // ---- argument parsing ----

    private static String schema(String properties, String... required) {
        String req = required.length == 0 ? "" : ",\"required\":[\"" + String.join("\",\"", required) + "\"]";
        return "{\"type\":\"object\",\"properties\":{" + properties + "}" + req + "}";
    }

    static LocalDate[] range(JsonNode args) {
        LocalDate end = args.hasNonNull("endDate") ? date(args, "endDate") : LocalDate.now(IST);
        LocalDate start = args.hasNonNull("startDate") ? date(args, "startDate") : end.minusDays(DEFAULT_DAYS - 1L);
        checkRange(start, end);
        return new LocalDate[] {start, end};
    }

    static void checkRange(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("The end date " + end + " is before the start date " + start + ".");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new IllegalArgumentException("Periods are limited to " + MAX_DAYS + " days; ask for a shorter one.");
        }
    }

    static LocalDate date(JsonNode args, String field) {
        if (!args.hasNonNull(field)) {
            throw new IllegalArgumentException("Missing " + field + " (use YYYY-MM-DD).");
        }
        try {
            return LocalDate.parse(args.get(field).asText());
        } catch (Exception e) {
            throw new IllegalArgumentException(field + " must be a date like 2026-11-20.");
        }
    }

    static List<UUID> branchIds(JsonNode args) {
        if (!args.hasNonNull("branchIds") || !args.get("branchIds").isArray() || args.get("branchIds").isEmpty()) {
            return null;
        }
        List<UUID> ids = new ArrayList<>();
        args.get("branchIds").forEach(n -> ids.add(uuid(n.asText())));
        return ids;
    }

    static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (Exception e) {
            throw new IllegalArgumentException("'" + s + "' is not a branch id; call list_branches to get ids.");
        }
    }
}
