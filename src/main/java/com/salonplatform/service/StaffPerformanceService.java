package com.salonplatform.service;

import com.salonplatform.domain.entity.BookingLineItem;
import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.Invoice;
import com.salonplatform.domain.entity.Staff;
import com.salonplatform.domain.repository.BookingLineItemRepository;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.InvoiceRepository;
import com.salonplatform.domain.repository.StaffRepository;
import com.salonplatform.dto.staff.*;
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
public class StaffPerformanceService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final StaffRepository staffRepository;
    private final BranchRepository branchRepository;
    private final InvoiceRepository invoiceRepository;
    private final BookingLineItemRepository lineItemRepository;

    public StaffTargetPerformanceResponse getTargetPerformance(
            LocalDate startDate, LocalDate endDate, List<UUID> branchIds) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();

        LocalDate start = startDate != null ? startDate : LocalDate.now(ZONE).withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now(ZONE);
        Instant rangeStart = start.atStartOfDay(ZONE).toInstant();
        Instant rangeEnd = end.plusDays(1).atStartOfDay(ZONE).toInstant();

        List<Staff> staffList = staffRepository.findByTenantId(tenantId).stream()
                .filter(Staff::isActive)
                .filter(s -> branchIds == null || branchIds.isEmpty() || branchIds.contains(s.getBranchId()))
                .collect(Collectors.toList());

        List<Invoice> invoices = invoiceRepository.findByTenantAndDateRange(tenantId, rangeStart, rangeEnd);
        if (branchIds != null && !branchIds.isEmpty()) {
            Set<UUID> branchSet = new HashSet<>(branchIds);
            invoices = invoices.stream().filter(i -> branchSet.contains(i.getBranchId())).collect(Collectors.toList());
        }

        Map<UUID, BigDecimal> salesByStaff = aggregateSales(invoices);

        long daysInPeriod = ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate today = LocalDate.now(ZONE);
        boolean isCurrentMonth = start.getYear() == today.getYear() && start.getMonth() == today.getMonth()
                && end.equals(today);
        long daysElapsed = isCurrentMonth ? today.getDayOfMonth() : daysInPeriod;

        List<StaffTargetPerformanceItem> items = new ArrayList<>();
        int meeting = 0;
        int below = 0;

        for (Staff staff : staffList) {
            BigDecimal target = staff.getMonthlySalesTarget() != null ? staff.getMonthlySalesTarget() : BigDecimal.ZERO;
            BigDecimal actual = salesByStaff.getOrDefault(staff.getId(), BigDecimal.ZERO);
            String branchName = branchRepository.findById(staff.getBranchId()).map(Branch::getName).orElse("—");

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

            BigDecimal incentivePct = staff.getIncentivePercent() != null ? staff.getIncentivePercent() : BigDecimal.ZERO;
            BigDecimal projectedIncentive = BigDecimal.ZERO;
            if (meetingTarget && target.compareTo(BigDecimal.ZERO) > 0 && incentivePct.compareTo(BigDecimal.ZERO) > 0) {
                projectedIncentive = target.multiply(incentivePct)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }

            if (target.compareTo(BigDecimal.ZERO) > 0) {
                if (meetingTarget) meeting++;
                else below++;
            }

            items.add(StaffTargetPerformanceItem.builder()
                    .staffId(staff.getId())
                    .staffName(staff.getName())
                    .branchId(staff.getBranchId())
                    .branchName(branchName)
                    .monthlySalesTarget(target)
                    .actualSales(actual)
                    .achievementPercent(achievementPercent)
                    .meetingTarget(meetingTarget)
                    .onTrack(onTrack)
                    .incentivePercent(incentivePct)
                    .projectedIncentive(projectedIncentive)
                    .build());
        }

        items.sort(Comparator.comparing(StaffTargetPerformanceItem::getAchievementPercent).reversed());

        String periodLabel = start.format(DateTimeFormatter.ofPattern("d MMM")) + " – "
                + end.format(DateTimeFormatter.ofPattern("d MMM yyyy"));

        return StaffTargetPerformanceResponse.builder()
                .periodLabel(periodLabel)
                .meetingTargetCount(meeting)
                .belowTargetCount(below)
                .staff(items)
                .build();
    }

    public StaffTargetTrendsResponse getTargetTrends(
            LocalDate startDate, LocalDate endDate, List<UUID> branchIds) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();

        LocalDate start = startDate != null ? startDate : LocalDate.now(ZONE).withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now(ZONE);
        Instant rangeStart = start.atStartOfDay(ZONE).toInstant();
        Instant rangeEnd = end.plusDays(1).atStartOfDay(ZONE).toInstant();

        List<Staff> staffList = staffRepository.findByTenantId(tenantId).stream()
                .filter(Staff::isActive)
                .filter(s -> s.getMonthlySalesTarget() != null
                        && s.getMonthlySalesTarget().compareTo(BigDecimal.ZERO) > 0)
                .filter(s -> branchIds == null || branchIds.isEmpty() || branchIds.contains(s.getBranchId()))
                .collect(Collectors.toList());

        List<Invoice> invoices = invoiceRepository.findByTenantAndDateRange(tenantId, rangeStart, rangeEnd);
        if (branchIds != null && !branchIds.isEmpty()) {
            Set<UUID> branchSet = new HashSet<>(branchIds);
            invoices = invoices.stream().filter(i -> branchSet.contains(i.getBranchId())).collect(Collectors.toList());
        }

        Map<UUID, Map<LocalDate, BigDecimal>> dailySalesByStaff = aggregateDailySalesByStaff(invoices);

        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            dates.add(d);
        }

        Map<UUID, List<StaffTargetTrend>> trendsByBranch = new LinkedHashMap<>();
        Map<UUID, String> branchNames = new HashMap<>();

        for (Staff staff : staffList) {
            BigDecimal target = staff.getMonthlySalesTarget();
            Map<LocalDate, BigDecimal> dailySales = dailySalesByStaff.getOrDefault(staff.getId(), Map.of());

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

            String branchName = branchRepository.findById(staff.getBranchId()).map(Branch::getName).orElse("—");
            branchNames.putIfAbsent(staff.getBranchId(), branchName);

            StaffTargetTrend trend = StaffTargetTrend.builder()
                    .staffId(staff.getId())
                    .staffName(staff.getName())
                    .branchId(staff.getBranchId())
                    .branchName(branchName)
                    .monthlySalesTarget(target)
                    .points(points)
                    .actualChangePct(changePctFromSeries(dailyActuals))
                    .gapChangePct(changePctFromSeries(dailyGaps))
                    .build();

            trendsByBranch.computeIfAbsent(staff.getBranchId(), k -> new ArrayList<>()).add(trend);
        }

        List<BranchStaffTargetTrends> branches = trendsByBranch.entrySet().stream()
                .map(e -> BranchStaffTargetTrends.builder()
                        .branchId(e.getKey())
                        .branchName(branchNames.get(e.getKey()))
                        .staff(e.getValue())
                        .build())
                .collect(Collectors.toList());

        String periodLabel = start.format(DateTimeFormatter.ofPattern("d MMM")) + " – "
                + end.format(DateTimeFormatter.ofPattern("d MMM yyyy"));

        return StaffTargetTrendsResponse.builder()
                .periodLabel(periodLabel)
                .branches(branches)
                .build();
    }

    private Map<UUID, BigDecimal> aggregateSales(List<Invoice> invoices) {
        Map<UUID, BigDecimal> sales = new HashMap<>();
        for (Invoice inv : invoices) {
            List<BookingLineItem> lines = lineItemRepository.findByBookingId(inv.getBookingId());
            for (BookingLineItem line : lines) {
                sales.merge(line.getStaffId(), line.getUnitPrice(), BigDecimal::add);
            }
        }
        return sales;
    }

    private Map<UUID, Map<LocalDate, BigDecimal>> aggregateDailySalesByStaff(List<Invoice> invoices) {
        Map<UUID, Map<LocalDate, BigDecimal>> result = new HashMap<>();
        for (Invoice inv : invoices) {
            LocalDate day = inv.getIssuedAt().atZone(ZONE).toLocalDate();
            List<BookingLineItem> lines = lineItemRepository.findByBookingId(inv.getBookingId());
            for (BookingLineItem line : lines) {
                result.computeIfAbsent(line.getStaffId(), k -> new HashMap<>())
                        .merge(day, line.getUnitPrice(), BigDecimal::add);
            }
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
