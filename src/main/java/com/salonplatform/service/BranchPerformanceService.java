package com.salonplatform.service;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.Invoice;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.InvoiceRepository;
import com.salonplatform.dto.branch.BranchTargetPerformanceItem;
import com.salonplatform.dto.branch.BranchTargetPerformanceResponse;
import com.salonplatform.dto.branch.BranchTargetTrend;
import com.salonplatform.dto.branch.BranchTargetTrendsResponse;
import com.salonplatform.dto.staff.StaffTargetTrendPoint;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BranchPerformanceService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final BranchRepository branchRepository;
    private final InvoiceRepository invoiceRepository;

    public BranchTargetPerformanceResponse getTargetPerformance(
            LocalDate startDate, LocalDate endDate, List<UUID> branchIds) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();

        LocalDate start = startDate != null ? startDate : LocalDate.now(ZONE).withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now(ZONE);
        Instant rangeStart = start.atStartOfDay(ZONE).toInstant();
        Instant rangeEnd = end.plusDays(1).atStartOfDay(ZONE).toInstant();

        List<Branch> branches = branchRepository.findByTenantId(tenantId).stream()
                .filter(b -> branchIds == null || branchIds.isEmpty() || branchIds.contains(b.getId()))
                .collect(Collectors.toList());

        List<Invoice> invoices = invoiceRepository.findByTenantAndDateRange(tenantId, rangeStart, rangeEnd);
        Map<UUID, BigDecimal> salesByBranch = aggregateBranchSales(invoices);

        long daysInPeriod = ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate today = LocalDate.now(ZONE);
        boolean isCurrentMonth = start.getYear() == today.getYear() && start.getMonth() == today.getMonth()
                && end.equals(today);
        long daysElapsed = isCurrentMonth ? today.getDayOfMonth() : daysInPeriod;

        List<BranchTargetPerformanceItem> items = new ArrayList<>();
        int meeting = 0;
        int below = 0;

        for (Branch branch : branches) {
            BigDecimal target = branch.getMonthlySalesTarget() != null
                    ? branch.getMonthlySalesTarget() : BigDecimal.ZERO;
            BigDecimal actual = salesByBranch.getOrDefault(branch.getId(), BigDecimal.ZERO);

            BigDecimal achievementPercent = BigDecimal.ZERO;
            if (target.compareTo(BigDecimal.ZERO) > 0) {
                achievementPercent = actual.multiply(BigDecimal.valueOf(100))
                        .divide(target, 1, RoundingMode.HALF_UP);
            }

            boolean meetingTarget = target.compareTo(BigDecimal.ZERO) > 0 && actual.compareTo(target) >= 0;
            boolean onTrack = meetingTarget;
            if (!meetingTarget && target.compareTo(BigDecimal.ZERO) > 0 && daysElapsed > 0) {
                BigDecimal expectedSoFar = target.multiply(BigDecimal.valueOf(daysElapsed))
                        .divide(BigDecimal.valueOf(daysInPeriod), 2, RoundingMode.HALF_UP);
                onTrack = actual.compareTo(expectedSoFar) >= 0;
            }

            if (target.compareTo(BigDecimal.ZERO) > 0) {
                if (meetingTarget) meeting++;
                else below++;
            }

            items.add(BranchTargetPerformanceItem.builder()
                    .branchId(branch.getId())
                    .branchName(branch.getName())
                    .monthlySalesTarget(target)
                    .actualSales(actual)
                    .achievementPercent(achievementPercent)
                    .meetingTarget(meetingTarget)
                    .onTrack(onTrack)
                    .build());
        }

        items.sort(Comparator.comparing(BranchTargetPerformanceItem::getAchievementPercent).reversed());

        String periodLabel = start.format(DateTimeFormatter.ofPattern("d MMM")) + " – "
                + end.format(DateTimeFormatter.ofPattern("d MMM yyyy"));

        return BranchTargetPerformanceResponse.builder()
                .periodLabel(periodLabel)
                .meetingTargetCount(meeting)
                .belowTargetCount(below)
                .branches(items)
                .build();
    }

    public BranchTargetTrendsResponse getTargetTrends(
            LocalDate startDate, LocalDate endDate, List<UUID> branchIds) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();

        LocalDate start = startDate != null ? startDate : LocalDate.now(ZONE).withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now(ZONE);
        Instant rangeStart = start.atStartOfDay(ZONE).toInstant();
        Instant rangeEnd = end.plusDays(1).atStartOfDay(ZONE).toInstant();

        List<Branch> branches = branchRepository.findByTenantId(tenantId).stream()
                .filter(b -> b.getMonthlySalesTarget() != null
                        && b.getMonthlySalesTarget().compareTo(BigDecimal.ZERO) > 0)
                .filter(b -> branchIds == null || branchIds.isEmpty() || branchIds.contains(b.getId()))
                .collect(Collectors.toList());

        List<Invoice> invoices = invoiceRepository.findByTenantAndDateRange(tenantId, rangeStart, rangeEnd);
        if (branchIds != null && !branchIds.isEmpty()) {
            Set<UUID> branchSet = new HashSet<>(branchIds);
            invoices = invoices.stream().filter(i -> branchSet.contains(i.getBranchId())).collect(Collectors.toList());
        }

        Map<UUID, Map<LocalDate, BigDecimal>> dailySalesByBranch = aggregateDailySalesByBranch(invoices);

        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            dates.add(d);
        }

        List<BranchTargetTrend> trends = new ArrayList<>();

        for (Branch branch : branches) {
            BigDecimal target = branch.getMonthlySalesTarget();
            Map<LocalDate, BigDecimal> dailySales = dailySalesByBranch.getOrDefault(branch.getId(), Map.of());

            BigDecimal cumulativeActual = BigDecimal.ZERO;
            List<StaffTargetTrendPoint> points = new ArrayList<>();
            List<BigDecimal> dailyActuals = new ArrayList<>();
            List<BigDecimal> dailyGaps = new ArrayList<>();

            for (LocalDate day : dates) {
                BigDecimal dayActual = dailySales.getOrDefault(day, BigDecimal.ZERO);
                cumulativeActual = cumulativeActual.add(dayActual);

                int daysInMonth = day.lengthOfMonth();
                BigDecimal ideal = target.multiply(BigDecimal.valueOf(day.getDayOfMonth()))
                        .divide(BigDecimal.valueOf(daysInMonth), 2, RoundingMode.HALF_UP);
                BigDecimal gap = cumulativeActual.subtract(ideal);

                points.add(StaffTargetTrendPoint.builder()
                        .date(day)
                        .actualCumulative(cumulativeActual)
                        .idealCumulative(ideal)
                        .gap(gap)
                        .build());

                dailyActuals.add(dayActual);
                dailyGaps.add(gap);
            }

            trends.add(BranchTargetTrend.builder()
                    .branchId(branch.getId())
                    .branchName(branch.getName())
                    .monthlySalesTarget(target)
                    .points(points)
                    .actualChangePct(changePctFromSeries(dailyActuals))
                    .gapChangePct(changePctFromSeries(dailyGaps))
                    .build());
        }

        String periodLabel = start.format(DateTimeFormatter.ofPattern("d MMM")) + " – "
                + end.format(DateTimeFormatter.ofPattern("d MMM yyyy"));

        return BranchTargetTrendsResponse.builder()
                .periodLabel(periodLabel)
                .branches(trends)
                .build();
    }

    private Map<UUID, BigDecimal> aggregateBranchSales(List<Invoice> invoices) {
        Map<UUID, BigDecimal> sales = new HashMap<>();
        for (Invoice inv : invoices) {
            sales.merge(inv.getBranchId(), inv.getGrandTotal(), BigDecimal::add);
        }
        return sales;
    }

    private Map<UUID, Map<LocalDate, BigDecimal>> aggregateDailySalesByBranch(List<Invoice> invoices) {
        Map<UUID, Map<LocalDate, BigDecimal>> result = new HashMap<>();
        for (Invoice inv : invoices) {
            LocalDate day = inv.getIssuedAt().atZone(ZONE).toLocalDate();
            result.computeIfAbsent(inv.getBranchId(), k -> new HashMap<>())
                    .merge(day, inv.getGrandTotal(), BigDecimal::add);
        }
        return result;
    }

    private BigDecimal changePctFromSeries(List<BigDecimal> values) {
        if (values.size() < 2) return null;
        int mid = values.size() / 2;
        BigDecimal first = values.subList(0, mid).stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal second = values.subList(mid, values.size()).stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (first.compareTo(BigDecimal.ZERO) == 0) {
            return second.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }
        return second.subtract(first)
                .multiply(BigDecimal.valueOf(100))
                .divide(first, 1, RoundingMode.HALF_UP);
    }
}
