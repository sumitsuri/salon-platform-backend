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
    private final DemandInsightsService demand;
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
                "Sales per staff member for a period, against their targets.",
                schema(RANGE_PROPS + "," + BRANCH_PROP),
                args -> {
                    LocalDate[] r = range(args);
                    return analytics.getStaffSalesPerformance(r[0], r[1], branchIds(args));
                }));

        t.add(new CopilotTool("attendance_overview", "Checking attendance",
                "Staff attendance for a period: who was present, late, absent or checked in outside the branch.",
                schema(RANGE_PROPS + "," + BRANCH_PROP),
                args -> {
                    LocalDate[] r = range(args);
                    return attendance.getDashboard(r[0], r[1], branchIds(args));
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
