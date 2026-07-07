package com.salonplatform.service;

import com.salonplatform.domain.entity.*;
import com.salonplatform.domain.enums.PaymentMode;
import com.salonplatform.domain.repository.*;
import com.salonplatform.dto.analytics.*;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final InvoiceRepository invoiceRepository;
    private final BookingLineItemRepository lineItemRepository;
    private final StaffRepository staffRepository;
    private final BranchRepository branchRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentSplitRepository paymentSplitRepository;

    public DashboardResponse getDashboard(LocalDate startDate, LocalDate endDate, List<UUID> branchIds) {
        UUID tenantId = SecurityUtils.requireTenantId();
        SecurityUtils.assertBrandAdminOrAbove();

        ZoneId zone = ZoneId.of("Asia/Kolkata");
        final Instant rangeStart;
        final Instant rangeEnd;
        if (startDate != null || endDate != null) {
            LocalDate start = startDate != null ? startDate : endDate;
            LocalDate end = endDate != null ? endDate : startDate;
            rangeStart = start.atStartOfDay(zone).toInstant();
            rangeEnd = end.plusDays(1).atStartOfDay(zone).toInstant();
        } else {
            rangeStart = null;
            rangeEnd = null;
        }

        Set<UUID> branchFilter = branchIds != null && !branchIds.isEmpty()
                ? new HashSet<>(branchIds) : null;

        List<Invoice> invoices = rangeStart == null
                ? invoiceRepository.findByTenantIdOrderByIssuedAtDesc(tenantId)
                : invoiceRepository.findByTenantAndDateRange(tenantId, rangeStart, rangeEnd);
        if (branchFilter != null) {
            invoices = invoices.stream().filter(i -> branchFilter.contains(i.getBranchId())).collect(Collectors.toList());
        }

        BigDecimal totalRevenue = invoices.stream().map(Invoice::getGrandTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        long visits = invoices.size();
        BigDecimal avgTicket = visits > 0
                ? totalRevenue.divide(BigDecimal.valueOf(visits), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal discounts = invoices.stream().map(Invoice::getDiscountAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<UUID, List<Invoice>> byBranch = invoices.stream().collect(Collectors.groupingBy(Invoice::getBranchId));

        // Include selected branches with zero stats when filter is active
        List<Branch> branchesToShow = branchFilter != null
                ? branchRepository.findAllById(branchFilter)
                : branchRepository.findByTenantId(tenantId);

        List<BranchStats> branchStats = branchesToShow.stream().map(branch -> {
            List<Invoice> branchInvoices = byBranch.getOrDefault(branch.getId(), List.of());
            BigDecimal rev = branchInvoices.stream().map(Invoice::getGrandTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            long v = branchInvoices.size();
            BigDecimal avg = v > 0 ? rev.divide(BigDecimal.valueOf(v), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            return BranchStats.builder()
                    .branchId(branch.getId())
                    .branchName(branch.getName())
                    .revenue(rev)
                    .visits(v)
                    .avgTicket(avg)
                    .build();
        }).sorted(Comparator.comparing(BranchStats::getRevenue).reversed()).collect(Collectors.toList());

        Map<String, ServiceStats> serviceMap = new HashMap<>();
        for (Invoice inv : invoices) {
            List<BookingLineItem> lines = lineItemRepository.findByBookingId(inv.getBookingId());
            for (BookingLineItem line : lines) {
                serviceMap.compute(line.getServiceName(), (k, v) -> {
                    if (v == null) return ServiceStats.builder().serviceName(k).revenue(line.getUnitPrice()).count(1).build();
                    return ServiceStats.builder().serviceName(k)
                            .revenue(v.getRevenue().add(line.getUnitPrice()))
                            .count(v.getCount() + 1).build();
                });
            }
        }
        List<ServiceStats> topServices = serviceMap.values().stream()
                .sorted(Comparator.comparing(ServiceStats::getRevenue).reversed())
                .limit(10).collect(Collectors.toList());

        Map<UUID, StaffStats> staffMap = new HashMap<>();
        for (Invoice inv : invoices) {
            List<BookingLineItem> lines = lineItemRepository.findByBookingId(inv.getBookingId());
            for (BookingLineItem line : lines) {
                staffMap.compute(line.getStaffId(), (k, v) -> {
                    Staff staff = staffRepository.findById(k).orElse(null);
                    String branchName = staff != null
                            ? branchRepository.findById(staff.getBranchId()).map(Branch::getName).orElse(null) : null;
                    if (v == null) {
                        return StaffStats.builder()
                                .staffId(k)
                                .staffName(staff != null ? staff.getName() : "Unknown")
                                .branchName(branchName)
                                .revenue(line.getUnitPrice())
                                .build();
                    }
                    return StaffStats.builder()
                            .staffId(k)
                            .staffName(v.getStaffName())
                            .branchName(v.getBranchName())
                            .revenue(v.getRevenue().add(line.getUnitPrice()))
                            .build();
                });
            }
        }
        List<StaffStats> topStaff = staffMap.values().stream()
                .sorted(Comparator.comparing(StaffStats::getRevenue).reversed())
                .limit(10).collect(Collectors.toList());

        List<Payment> payments = paymentRepository.findAll().stream()
                .filter(p -> p.getTenantId().equals(tenantId))
                .filter(p -> rangeStart == null || (!p.getPaidAt().isBefore(rangeStart) && p.getPaidAt().isBefore(rangeEnd)))
                .filter(p -> branchFilter == null || branchFilter.contains(p.getBranchId()))
                .collect(Collectors.toList());

        BigDecimal cash = sumByMode(payments, PaymentMode.CASH);
        BigDecimal upi = sumByMode(payments, PaymentMode.UPI);
        BigDecimal card = sumByMode(payments, PaymentMode.CARD);

        List<BranchTrend> branchTrends = buildBranchTrends(invoices, branchesToShow, startDate, endDate, zone);

        return DashboardResponse.builder()
                .totalRevenue(totalRevenue)
                .totalVisits(visits)
                .avgTicketSize(avgTicket)
                .totalDiscounts(discounts)
                .branchStats(branchStats)
                .branchTrends(branchTrends)
                .topServices(topServices)
                .topStaff(topStaff)
                .paymentMix(PaymentMix.builder().cash(cash).upi(upi).card(card).build())
                .build();
    }

    private List<BranchTrend> buildBranchTrends(List<Invoice> invoices, List<Branch> branches,
                                                LocalDate startDate, LocalDate endDate, ZoneId zone) {
        LocalDate trendStart;
        LocalDate trendEnd;
        if (startDate != null || endDate != null) {
            trendStart = startDate != null ? startDate : endDate;
            trendEnd = endDate != null ? endDate : startDate;
        } else if (invoices.isEmpty()) {
            trendStart = LocalDate.now(zone);
            trendEnd = trendStart;
        } else {
            trendEnd = invoices.stream()
                    .map(i -> i.getIssuedAt().atZone(zone).toLocalDate())
                    .max(LocalDate::compareTo)
                    .orElse(LocalDate.now(zone));
            trendStart = invoices.stream()
                    .map(i -> i.getIssuedAt().atZone(zone).toLocalDate())
                    .min(LocalDate::compareTo)
                    .orElse(trendEnd);
            long span = ChronoUnit.DAYS.between(trendStart, trendEnd) + 1;
            if (span > 60) {
                trendStart = trendEnd.minusDays(59);
            }
        }

        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = trendStart; !d.isAfter(trendEnd); d = d.plusDays(1)) {
            dates.add(d);
        }

        Map<UUID, Map<LocalDate, List<Invoice>>> grouped = new HashMap<>();
        for (Invoice inv : invoices) {
            LocalDate day = inv.getIssuedAt().atZone(zone).toLocalDate();
            grouped.computeIfAbsent(inv.getBranchId(), k -> new HashMap<>())
                    .computeIfAbsent(day, k -> new ArrayList<>())
                    .add(inv);
        }

        return branches.stream().map(branch -> {
            Map<LocalDate, List<Invoice>> branchByDay = grouped.getOrDefault(branch.getId(), Map.of());
            List<TrendPoint> points = dates.stream().map(day -> {
                List<Invoice> dayInvoices = branchByDay.getOrDefault(day, List.of());
                BigDecimal rev = dayInvoices.stream().map(Invoice::getGrandTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
                long v = dayInvoices.size();
                BigDecimal avg = v > 0 ? rev.divide(BigDecimal.valueOf(v), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                BigDecimal disc = dayInvoices.stream().map(Invoice::getDiscountAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                return TrendPoint.builder()
                        .date(day)
                        .revenue(rev)
                        .visits(v)
                        .avgTicket(avg)
                        .discounts(disc)
                        .build();
            }).collect(Collectors.toList());

            return BranchTrend.builder()
                    .branchId(branch.getId())
                    .branchName(branch.getName())
                    .points(points)
                    .revenueChangePct(changePct(points, TrendPoint::getRevenue))
                    .visitsChangePct(changePctLong(points, TrendPoint::getVisits))
                    .avgTicketChangePct(changePct(points, TrendPoint::getAvgTicket))
                    .discountsChangePct(changePct(points, TrendPoint::getDiscounts))
                    .build();
        }).collect(Collectors.toList());
    }

    private BigDecimal changePct(List<TrendPoint> points, java.util.function.Function<TrendPoint, BigDecimal> getter) {
        if (points.size() < 2) return null;
        int mid = points.size() / 2;
        BigDecimal first = points.subList(0, mid).stream().map(getter).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal second = points.subList(mid, points.size()).stream().map(getter).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (first.compareTo(BigDecimal.ZERO) == 0) {
            return second.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }
        return second.subtract(first)
                .multiply(BigDecimal.valueOf(100))
                .divide(first, 1, RoundingMode.HALF_UP);
    }

    private BigDecimal changePctLong(List<TrendPoint> points, java.util.function.ToLongFunction<TrendPoint> getter) {
        if (points.size() < 2) return null;
        int mid = points.size() / 2;
        long first = points.subList(0, mid).stream().mapToLong(getter).sum();
        long second = points.subList(mid, points.size()).stream().mapToLong(getter).sum();
        if (first == 0) {
            return second > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(second - first)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(first), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal sumByMode(List<Payment> payments, PaymentMode mode) {
        BigDecimal direct = payments.stream()
                .filter(p -> p.getMode() == mode)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal fromSplits = payments.stream()
                .filter(p -> p.getMode() == PaymentMode.SPLIT)
                .flatMap(p -> paymentSplitRepository.findByPaymentId(p.getId()).stream())
                .filter(s -> s.getMode() == mode)
                .map(PaymentSplit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return direct.add(fromSplits);
    }
}
