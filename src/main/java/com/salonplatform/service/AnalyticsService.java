package com.salonplatform.service;

import com.salonplatform.domain.entity.*;
import com.salonplatform.domain.enums.PaymentMode;
import com.salonplatform.domain.repository.*;
import com.salonplatform.dto.analytics.*;
import com.salonplatform.exception.ForbiddenException;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.security.UserPrincipal;
import com.salonplatform.util.PageUtils;
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
    private final RecommendationService recommendationService;
    private final WeekdaySalesService weekdaySalesService;

    public DashboardResponse getDashboard(LocalDate startDate, LocalDate endDate, List<UUID> branchIds) {
        SecurityUtils.assertBrandAdminOrAbove();
        return computeDashboard(SecurityUtils.requireTenantId(), startDate, endDate, branchIds);
    }

    public RecommendationsResponse getRecommendations(LocalDate startDate, LocalDate endDate, List<UUID> branchIds) {
        UserPrincipal user = SecurityUtils.currentUser();
        UUID tenantId = SecurityUtils.requireTenantId();
        List<UUID> resolvedBranchIds = resolveBranchIds(user, branchIds);
        List<Invoice> invoices = fetchInvoices(tenantId, startDate, endDate, resolvedBranchIds);
        DashboardResponse dashboard = computeDashboard(tenantId, startDate, endDate, resolvedBranchIds);
        RecommendationsResponse response = recommendationService.generate(dashboard, user.getRole());
        List<WeekdaySalesInsight> weekdayInsights = weekdaySalesService.analyze(invoices, startDate, endDate, dashboard);
        return RecommendationsResponse.builder()
                .brandWide(response.getBrandWide())
                .branches(response.getBranches())
                .weekdayInsights(weekdayInsights)
                .build();
    }

    public ServiceContributionResponse getServiceContribution(
            LocalDate startDate,
            LocalDate endDate,
            List<UUID> branchIds,
            String serviceName,
            int page,
            int size) {
        UserPrincipal user = SecurityUtils.currentUser();
        UUID tenantId = SecurityUtils.requireTenantId();
        List<UUID> resolvedBranchIds = resolveBranchIds(user, branchIds);
        List<Invoice> invoices = fetchInvoices(tenantId, startDate, endDate, resolvedBranchIds);

        BigDecimal totalRevenue = invoices.stream()
                .map(Invoice::getGrandTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, ServiceAccumulator> byService = new HashMap<>();
        for (Invoice invoice : invoices) {
            List<BookingLineItem> lines = lineItemRepository.findByBookingId(invoice.getBookingId());
            for (BookingLineItem line : lines) {
                if (line.getServiceName() == null) continue;
                byService.compute(line.getServiceName(), (k, acc) -> {
                    ServiceAccumulator current = acc == null ? new ServiceAccumulator() : acc;
                    return current.add(line.getUnitPrice());
                });
            }
        }

        BigDecimal serviceRevenue = byService.values().stream()
                .map(ServiceAccumulator::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalCount = byService.values().stream().mapToLong(ServiceAccumulator::count).sum();

        List<ServiceContributionItem> services = byService.entrySet().stream()
                .map(e -> {
                    ServiceAccumulator acc = e.getValue();
                    double revPct = serviceRevenue.compareTo(BigDecimal.ZERO) > 0
                            ? acc.revenue().multiply(BigDecimal.valueOf(100))
                            .divide(serviceRevenue, 2, RoundingMode.HALF_UP).doubleValue()
                            : 0;
                    double countPct = totalCount > 0
                            ? Math.round(acc.count() * 10000.0 / totalCount) / 100.0
                            : 0;
                    return ServiceContributionItem.builder()
                            .serviceName(e.getKey())
                            .revenue(acc.revenue())
                            .count(acc.count())
                            .revenueSharePct(revPct)
                            .countSharePct(countPct)
                            .build();
                })
                .sorted(Comparator.comparing(ServiceContributionItem::getRevenue).reversed())
                .collect(Collectors.toList());

        if (serviceName != null && !serviceName.isBlank()) {
            String q = serviceName.trim().toLowerCase();
            services = services.stream()
                    .filter(s -> s.getServiceName().toLowerCase().contains(q))
                    .collect(Collectors.toList());
        }

        var paged = PageUtils.slice(services, page, size);

        return ServiceContributionResponse.builder()
                .totalRevenue(totalRevenue)
                .serviceRevenue(serviceRevenue)
                .totalServiceCount(totalCount)
                .services(paged.getContent())
                .page(paged.getPage())
                .size(paged.getSize())
                .totalElements(paged.getTotalElements())
                .totalPages(paged.getTotalPages())
                .build();
    }

    private List<Invoice> fetchInvoices(UUID tenantId, LocalDate startDate, LocalDate endDate, List<UUID> branchIds) {
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
            invoices = invoices.stream()
                    .filter(i -> branchFilter.contains(i.getBranchId()))
                    .collect(Collectors.toList());
        }
        return invoices;
    }

    private record ServiceAccumulator(BigDecimal revenue, long count) {
        ServiceAccumulator() {
            this(BigDecimal.ZERO, 0);
        }

        ServiceAccumulator add(BigDecimal amount) {
            return new ServiceAccumulator(revenue.add(amount), count + 1);
        }
    }

    private List<UUID> resolveBranchIds(UserPrincipal user, List<UUID> branchIds) {
        if (SecurityUtils.isManagerRole()) {
            if (user.getBranchId() == null) {
                throw new ForbiddenException("Branch context required");
            }
            SecurityUtils.assertBranchAccess(user.getBranchId());
            return List.of(user.getBranchId());
        }
        SecurityUtils.assertBrandAdminOrAbove();
        return branchIds;
    }

    private DashboardResponse computeDashboard(UUID tenantId, LocalDate startDate, LocalDate endDate, List<UUID> branchIds) {
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

        List<Invoice> invoices = fetchInvoices(tenantId, startDate, endDate, branchIds);
        Set<UUID> branchFilter = branchIds != null && !branchIds.isEmpty()
                ? new HashSet<>(branchIds) : null;

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
            BigDecimal branchDiscounts = branchInvoices.stream()
                    .map(Invoice::getDiscountAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return BranchStats.builder()
                    .branchId(branch.getId())
                    .branchName(branch.getName())
                    .revenue(rev)
                    .visits(v)
                    .avgTicket(avg)
                    .discountAmount(branchDiscounts)
                    .topStaff(buildBranchStaffStats(branchInvoices, branch.getName()))
                    .topServices(buildBranchServiceStats(branchInvoices))
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

    private List<StaffStats> buildBranchStaffStats(List<Invoice> invoices, String branchName) {
        Map<UUID, StaffStats> staffMap = new HashMap<>();
        for (Invoice inv : invoices) {
            List<BookingLineItem> lines = lineItemRepository.findByBookingId(inv.getBookingId());
            for (BookingLineItem line : lines) {
                staffMap.compute(line.getStaffId(), (k, v) -> {
                    Staff staff = staffRepository.findById(k).orElse(null);
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
                            .branchName(branchName)
                            .revenue(v.getRevenue().add(line.getUnitPrice()))
                            .build();
                });
            }
        }
        return staffMap.values().stream()
                .sorted(Comparator.comparing(StaffStats::getRevenue).reversed())
                .limit(10)
                .collect(Collectors.toList());
    }

    private List<ServiceStats> buildBranchServiceStats(List<Invoice> invoices) {
        Map<String, ServiceStats> serviceMap = new HashMap<>();
        for (Invoice inv : invoices) {
            List<BookingLineItem> lines = lineItemRepository.findByBookingId(inv.getBookingId());
            for (BookingLineItem line : lines) {
                serviceMap.compute(line.getServiceName(), (k, v) -> {
                    if (v == null) {
                        return ServiceStats.builder().serviceName(k).revenue(line.getUnitPrice()).count(1).build();
                    }
                    return ServiceStats.builder()
                            .serviceName(k)
                            .revenue(v.getRevenue().add(line.getUnitPrice()))
                            .count(v.getCount() + 1)
                            .build();
                });
            }
        }
        return serviceMap.values().stream()
                .sorted(Comparator.comparing(ServiceStats::getRevenue).reversed())
                .limit(10)
                .collect(Collectors.toList());
    }
}
