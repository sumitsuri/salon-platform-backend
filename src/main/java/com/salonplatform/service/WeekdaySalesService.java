package com.salonplatform.service;

import com.salonplatform.domain.entity.Invoice;
import com.salonplatform.dto.analytics.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WeekdaySalesService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final double SLOW_DAY_THRESHOLD = 0.85;
    private static final int MIN_VISITS_FOR_ANALYSIS = 8;

    public List<WeekdaySalesInsight> analyze(
            List<Invoice> invoices,
            LocalDate startDate,
            LocalDate endDate,
            DashboardResponse dashboard) {

        Map<UUID, Map<DayOfWeek, DayAccumulator>> byBranchDay = new HashMap<>();
        for (Invoice invoice : invoices) {
            if (invoice.getIssuedAt() == null) continue;
            DayOfWeek dow = invoice.getIssuedAt().atZone(ZONE).getDayOfWeek();
            byBranchDay
                    .computeIfAbsent(invoice.getBranchId(), k -> new EnumMap<>(DayOfWeek.class))
                    .compute(dow, (k, acc) -> {
                        DayAccumulator current = acc == null ? new DayAccumulator() : acc;
                        return current.add(invoice.getGrandTotal());
                    });
        }

        Map<DayOfWeek, Integer> occurrences = countDayOccurrences(startDate, endDate);

        return dashboard.getBranchStats().stream()
                .map(branch -> buildBranchInsight(
                        branch,
                        byBranchDay.getOrDefault(branch.getBranchId(), Map.of()),
                        occurrences))
                .filter(insight -> !insight.getDayStats().isEmpty())
                .collect(Collectors.toList());
    }

    private WeekdaySalesInsight buildBranchInsight(
            BranchStats branch,
            Map<DayOfWeek, DayAccumulator> dayMap,
            Map<DayOfWeek, Integer> occurrences) {

        BigDecimal totalRevenue = dayMap.values().stream()
                .map(DayAccumulator::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalVisits = dayMap.values().stream().mapToLong(DayAccumulator::visits).sum();

        if (totalVisits < MIN_VISITS_FOR_ANALYSIS) {
            return WeekdaySalesInsight.builder()
                    .branchId(branch.getBranchId())
                    .branchName(branch.getBranchName())
                    .dayStats(List.of())
                    .slowDayActions(List.of())
                    .build();
        }

        List<DayOfWeekStat> dayStats = new ArrayList<>();
        double sumAvgPerDay = 0;
        int daysWithData = 0;

        for (DayOfWeek dow : DayOfWeek.values()) {
            DayAccumulator acc = dayMap.getOrDefault(dow, new DayAccumulator());
            int occ = Math.max(occurrences.getOrDefault(dow, 1), 1);
            double avgPerDay = acc.revenue().divide(BigDecimal.valueOf(occ), 2, RoundingMode.HALF_UP).doubleValue();
            if (acc.visits() > 0) {
                sumAvgPerDay += avgPerDay;
                daysWithData++;
            }
            dayStats.add(DayOfWeekStat.builder()
                    .day(dow.name())
                    .dayLabel(formatDay(dow))
                    .revenue(acc.revenue())
                    .visits(acc.visits())
                    .avgRevenuePerDay(avgPerDay)
                    .build());
        }

        double weeklyAvg = daysWithData > 0 ? sumAvgPerDay / daysWithData : 0;
        for (DayOfWeekStat stat : dayStats) {
            double vsAvg = weeklyAvg > 0
                    ? Math.round((stat.getAvgRevenuePerDay() / weeklyAvg - 1) * 1000) / 10.0
                    : 0;
            stat.setVsWeeklyAvgPct(vsAvg);
            stat.setSlowDay(stat.getVisits() > 0 && stat.getAvgRevenuePerDay() < weeklyAvg * SLOW_DAY_THRESHOLD);
        }

        dayStats.sort(Comparator.comparingDouble(DayOfWeekStat::getAvgRevenuePerDay).reversed());

        List<ServiceStats> services = branch.getTopServices() != null ? branch.getTopServices() : List.of();
        ServiceStats hero = services.isEmpty() ? null : services.get(0);
        ServiceStats underused = services.size() >= 2 ? services.get(services.size() - 1) : null;

        List<SlowDayAction> slowDayActions = dayStats.stream()
                .filter(DayOfWeekStat::isSlowDay)
                .sorted(Comparator.comparingDouble(DayOfWeekStat::getVsWeeklyAvgPct))
                .limit(2)
                .map(stat -> buildSlowDayAction(stat, hero, underused, branch.getBranchName()))
                .collect(Collectors.toList());

        dayStats.sort(Comparator.comparing(stat -> dayOrder(DayOfWeek.valueOf(stat.getDay()))));

        return WeekdaySalesInsight.builder()
                .branchId(branch.getBranchId())
                .branchName(branch.getBranchName())
                .dayStats(dayStats)
                .slowDayActions(slowDayActions)
                .build();
    }

    private SlowDayAction buildSlowDayAction(
            DayOfWeekStat stat,
            ServiceStats hero,
            ServiceStats underused,
            String branchName) {

        double gap = Math.abs(stat.getVsWeeklyAvgPct());
        String severity = gap >= 25 ? "HIGH" : gap >= 15 ? "MEDIUM" : "LOW";

        List<String> actions = new ArrayList<>();
        actions.add("Post a " + stat.getDayLabel() + " offer on society WhatsApp groups and at reception before 10 AM.");
        actions.add("Run a \"" + stat.getDayLabel() + " walk-in hour\" with express slots — no booking needed.");

        if (hero != null) {
            actions.add("Create a " + stat.getDayLabel() + " combo: " + hero.getServiceName()
                    + " + quick add-on at 10–15% off for same-day walk-ins.");
            actions.add("Coach staff to upsell " + hero.getServiceName() + " during quieter " + stat.getDayLabel() + " slots.");
        }

        if (underused != null && underused.getCount() <= 5) {
            actions.add("Bundle underused " + underused.getServiceName() + " with a top service as a mid-week package on "
                    + stat.getDayLabel() + "s.");
        }

        actions.add("Message customers inactive 30+ days — offer a " + stat.getDayLabel() + " appointment slot.");
        actions.add("Place a society notice / lift poster highlighting " + stat.getDayLabel() + "-only pricing at " + branchName + ".");

        return SlowDayAction.builder()
                .day(stat.getDay())
                .dayLabel(stat.getDayLabel())
                .severity(severity)
                .headline("Boost " + stat.getDayLabel() + " sales")
                .insight(stat.getDayLabel() + " averages " + formatCurrency(BigDecimal.valueOf(stat.getAvgRevenuePerDay()))
                        + " per day — " + gap + "% below your branch's weekday average. Use targeted offers to lift footfall.")
                .metricLabel("Gap vs avg")
                .metricValue(stat.getVsWeeklyAvgPct() + "%")
                .actions(actions.stream().limit(5).collect(Collectors.toList()))
                .build();
    }

    private Map<DayOfWeek, Integer> countDayOccurrences(LocalDate startDate, LocalDate endDate) {
        Map<DayOfWeek, Integer> counts = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek dow : DayOfWeek.values()) {
            counts.put(dow, 0);
        }

        if (startDate == null || endDate == null) {
            for (DayOfWeek dow : DayOfWeek.values()) {
                counts.put(dow, 8);
            }
            return counts;
        }

        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            counts.merge(cursor.getDayOfWeek(), 1, Integer::sum);
            cursor = cursor.plusDays(1);
        }
        return counts;
    }

    private int dayOrder(DayOfWeek dow) {
        return dow.getValue();
    }

    private String formatDay(DayOfWeek dow) {
        return dow.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    private String formatCurrency(BigDecimal amount) {
        return "₹" + amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private record DayAccumulator(BigDecimal revenue, long visits) {
        DayAccumulator() {
            this(BigDecimal.ZERO, 0);
        }

        DayAccumulator add(BigDecimal amount) {
            return new DayAccumulator(revenue.add(amount), visits + 1);
        }
    }
}
