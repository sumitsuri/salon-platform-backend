package com.salonplatform.copilot.tools;

import com.salonplatform.dto.analytics.BranchStats;
import com.salonplatform.dto.analytics.DashboardResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Explains a change in revenue between two periods, in code rather than by the model.
 *
 * <p>Revenue is visits x average ticket, so the change splits exactly into the part caused by fewer or more
 * visits (priced at the earlier ticket) and the part caused by a different ticket (on the later visit count).
 * The two parts always add up to the total change.
 */
public final class PeriodComparison {

    private PeriodComparison() {}

    public static Map<String, Object> compare(DashboardResponse earlier, DashboardResponse later) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("overall", row("All branches",
                money(earlier.getTotalRevenue()), earlier.getTotalVisits(),
                money(later.getTotalRevenue()), later.getTotalVisits()));

        Map<UUID, BranchStats> a = byBranch(earlier);
        Map<UUID, BranchStats> b = byBranch(later);
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<UUID, String> names = new LinkedHashMap<>();
        a.forEach((id, s) -> names.put(id, s.getBranchName()));
        b.forEach((id, s) -> names.putIfAbsent(id, s.getBranchName()));
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            BranchStats sa = a.get(e.getKey());
            BranchStats sb = b.get(e.getKey());
            rows.add(row(e.getValue(),
                    sa == null ? 0 : money(sa.getRevenue()), sa == null ? 0 : sa.getVisits(),
                    sb == null ? 0 : money(sb.getRevenue()), sb == null ? 0 : sb.getVisits()));
        }
        rows.sort(Comparator.comparingDouble(r -> ((Number) r.get("revenueChange")).doubleValue()));
        out.put("byBranchWorstFirst", rows);
        out.put("howToRead", "revenueChange = visitsEffect + ticketEffect. visitsEffect is the change in visits "
                + "priced at the earlier average ticket; ticketEffect is the change in average ticket on the later visits.");
        return out;
    }

    static Map<String, Object> row(String name, double revenueA, long visitsA, double revenueB, long visitsB) {
        double ticketA = visitsA == 0 ? 0 : revenueA / visitsA;
        double ticketB = visitsB == 0 ? 0 : revenueB / visitsB;
        double change = revenueB - revenueA;
        double visitsEffect = (visitsB - visitsA) * ticketA;
        double ticketEffect = (ticketB - ticketA) * visitsB;
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("name", name);
        r.put("revenueBefore", Math.round(revenueA));
        r.put("revenueAfter", Math.round(revenueB));
        r.put("revenueChange", Math.round(change));
        r.put("changePercent", revenueA == 0 ? null : round1(change / revenueA * 100));
        r.put("visitsBefore", visitsA);
        r.put("visitsAfter", visitsB);
        r.put("avgTicketBefore", Math.round(ticketA));
        r.put("avgTicketAfter", Math.round(ticketB));
        r.put("visitsEffect", Math.round(visitsEffect));
        r.put("ticketEffect", Math.round(ticketEffect));
        return r;
    }

    private static Map<UUID, BranchStats> byBranch(DashboardResponse d) {
        Map<UUID, BranchStats> m = new LinkedHashMap<>();
        if (d.getBranchStats() != null) {
            d.getBranchStats().forEach(s -> m.put(s.getBranchId(), s));
        }
        return m;
    }

    private static double money(BigDecimal v) {
        return v == null ? 0 : v.doubleValue();
    }

    private static double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
