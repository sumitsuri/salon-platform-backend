package com.salonplatform.service;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.BranchExpenditure;
import com.salonplatform.domain.entity.Invoice;
import com.salonplatform.domain.enums.ExpenditureCategory;
import com.salonplatform.domain.repository.BranchExpenditureRepository;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.InvoiceRepository;
import com.salonplatform.dto.analytics.*;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlAnalyticsService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final int DEFAULT_TREND_MONTHS = 6;

    private final BranchRepository branchRepository;
    private final InvoiceRepository invoiceRepository;
    private final BranchExpenditureRepository expenditureRepository;

    public PlSummaryResponse getPlSummary(LocalDate startDate, LocalDate endDate, List<UUID> branchIds) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();

        LocalDate start = startDate != null ? startDate : LocalDate.now(ZONE).withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now(ZONE);

        LocalDate monthStart = start.withDayOfMonth(1);
        LocalDate monthEnd = end.withDayOfMonth(1);

        Instant rangeStart = start.atStartOfDay(ZONE).toInstant();
        Instant rangeEnd = end.plusDays(1).atStartOfDay(ZONE).toInstant();

        List<Branch> branches = branchRepository.findByTenantId(tenantId).stream()
                .filter(b -> branchIds == null || branchIds.isEmpty() || branchIds.contains(b.getId()))
                .collect(Collectors.toList());

        List<Invoice> invoices = invoiceRepository.findByTenantAndDateRange(tenantId, rangeStart, rangeEnd);
        if (branchIds != null && !branchIds.isEmpty()) {
            Set<UUID> branchSet = new HashSet<>(branchIds);
            invoices = invoices.stream().filter(i -> branchSet.contains(i.getBranchId())).collect(Collectors.toList());
        }

        Map<UUID, BigDecimal> revenueByBranch = new HashMap<>();
        for (Invoice inv : invoices) {
            revenueByBranch.merge(inv.getBranchId(), inv.getGrandTotal(), BigDecimal::add);
        }

        List<BranchExpenditure> expenditures = expenditureRepository
                .findByTenantIdAndExpenseMonthBetweenAndActiveTrue(tenantId, monthStart, monthEnd);
        if (branchIds != null && !branchIds.isEmpty()) {
            Set<UUID> branchSet = new HashSet<>(branchIds);
            expenditures = expenditures.stream().filter(e -> branchSet.contains(e.getBranchId())).collect(Collectors.toList());
        }

        Map<UUID, Map<ExpenditureCategory, BigDecimal>> expenseByBranch = new HashMap<>();
        for (BranchExpenditure e : expenditures) {
            expenseByBranch
                    .computeIfAbsent(e.getBranchId(), k -> new EnumMap<>(ExpenditureCategory.class))
                    .merge(e.getCategory(), e.getAmount(), BigDecimal::add);
        }

        List<BranchPlSummary> branchSummaries = new ArrayList<>();
        Map<ExpenditureCategory, BigDecimal> brandExpenses = new EnumMap<>(ExpenditureCategory.class);
        BigDecimal brandRevenue = BigDecimal.ZERO;
        BigDecimal brandTotalExpenses = BigDecimal.ZERO;

        for (Branch branch : branches) {
            BigDecimal revenue = revenueByBranch.getOrDefault(branch.getId(), BigDecimal.ZERO);
            Map<ExpenditureCategory, BigDecimal> catMap = expenseByBranch.getOrDefault(branch.getId(), Map.of());
            List<PlCategoryAmount> byCategory = buildCategoryList(catMap);
            BigDecimal totalExpenses = catMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal netProfit = revenue.subtract(totalExpenses);

            branchSummaries.add(BranchPlSummary.builder()
                    .branchId(branch.getId())
                    .branchName(branch.getName())
                    .revenue(revenue)
                    .expensesByCategory(byCategory)
                    .totalExpenses(totalExpenses)
                    .netProfit(netProfit)
                    .marginPercent(marginPercent(revenue, netProfit))
                    .build());

            brandRevenue = brandRevenue.add(revenue);
            brandTotalExpenses = brandTotalExpenses.add(totalExpenses);
            for (Map.Entry<ExpenditureCategory, BigDecimal> entry : catMap.entrySet()) {
                brandExpenses.merge(entry.getKey(), entry.getValue(), BigDecimal::add);
            }
        }

        branchSummaries.sort(Comparator.comparing(BranchPlSummary::getNetProfit).reversed());

        BigDecimal brandNet = brandRevenue.subtract(brandTotalExpenses);
        BrandPlSummary brand = BrandPlSummary.builder()
                .revenue(brandRevenue)
                .expensesByCategory(buildCategoryList(brandExpenses))
                .totalExpenses(brandTotalExpenses)
                .netProfit(brandNet)
                .marginPercent(marginPercent(brandRevenue, brandNet))
                .build();

        String periodLabel = start.format(DateTimeFormatter.ofPattern("d MMM")) + " – "
                + end.format(DateTimeFormatter.ofPattern("d MMM yyyy"));

        return PlSummaryResponse.builder()
                .periodLabel(periodLabel)
                .brand(brand)
                .branches(branchSummaries)
                .build();
    }

    public PlTrendsResponse getPlTrends(LocalDate endMonth, Integer months, List<UUID> branchIds) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();

        LocalDate end = (endMonth != null ? endMonth : LocalDate.now(ZONE)).withDayOfMonth(1);
        int monthCount = months != null && months > 0 ? months : DEFAULT_TREND_MONTHS;
        LocalDate start = end.minusMonths(monthCount - 1L).withDayOfMonth(1);

        List<Branch> branches = branchRepository.findByTenantId(tenantId).stream()
                .filter(b -> branchIds == null || branchIds.isEmpty() || branchIds.contains(b.getId()))
                .collect(Collectors.toList());

        Instant rangeStart = start.atStartOfDay(ZONE).toInstant();
        LocalDate rangeEndDate = end.withDayOfMonth(end.lengthOfMonth());
        Instant rangeEnd = rangeEndDate.plusDays(1).atStartOfDay(ZONE).toInstant();

        List<Invoice> invoices = invoiceRepository.findByTenantAndDateRange(tenantId, rangeStart, rangeEnd);
        if (branchIds != null && !branchIds.isEmpty()) {
            Set<UUID> branchSet = new HashSet<>(branchIds);
            invoices = invoices.stream().filter(i -> branchSet.contains(i.getBranchId())).collect(Collectors.toList());
        }

        List<BranchExpenditure> expenditures = expenditureRepository
                .findByTenantIdAndExpenseMonthBetweenAndActiveTrue(tenantId, start, end);
        if (branchIds != null && !branchIds.isEmpty()) {
            Set<UUID> branchSet = new HashSet<>(branchIds);
            expenditures = expenditures.stream().filter(e -> branchSet.contains(e.getBranchId())).collect(Collectors.toList());
        }

        Map<UUID, Map<LocalDate, BigDecimal>> revenueByBranchMonth = aggregateRevenueByBranchMonth(invoices);
        Map<UUID, Map<LocalDate, BigDecimal>> expensesByBranchMonth = aggregateExpensesByBranchMonth(expenditures);

        List<LocalDate> monthPoints = new ArrayList<>();
        for (LocalDate m = start; !m.isAfter(end); m = m.plusMonths(1)) {
            monthPoints.add(m);
        }

        List<BranchPlTrend> trends = new ArrayList<>();
        for (Branch branch : branches) {
            Map<LocalDate, BigDecimal> revMap = revenueByBranchMonth.getOrDefault(branch.getId(), Map.of());
            Map<LocalDate, BigDecimal> expMap = expensesByBranchMonth.getOrDefault(branch.getId(), Map.of());

            List<PlTrendPoint> points = new ArrayList<>();
            List<BigDecimal> revenues = new ArrayList<>();
            List<BigDecimal> expenseTotals = new ArrayList<>();
            List<BigDecimal> netProfits = new ArrayList<>();
            List<BigDecimal> margins = new ArrayList<>();

            for (LocalDate month : monthPoints) {
                BigDecimal revenue = revMap.getOrDefault(month, BigDecimal.ZERO);
                BigDecimal totalExpenses = expMap.getOrDefault(month, BigDecimal.ZERO);
                BigDecimal netProfit = revenue.subtract(totalExpenses);
                BigDecimal margin = marginPercent(revenue, netProfit);

                points.add(PlTrendPoint.builder()
                        .month(month)
                        .revenue(revenue)
                        .totalExpenses(totalExpenses)
                        .netProfit(netProfit)
                        .marginPercent(margin)
                        .build());

                revenues.add(revenue);
                expenseTotals.add(totalExpenses);
                netProfits.add(netProfit);
                margins.add(margin);
            }

            trends.add(BranchPlTrend.builder()
                    .branchId(branch.getId())
                    .branchName(branch.getName())
                    .points(points)
                    .revenueChangePct(changePctFromSeries(revenues))
                    .expensesChangePct(changePctFromSeries(expenseTotals))
                    .netProfitChangePct(changePctFromSeries(netProfits))
                    .marginChangePct(changePctFromSeries(margins))
                    .build());
        }

        String periodLabel = start.format(DateTimeFormatter.ofPattern("MMM yyyy")) + " – "
                + end.format(DateTimeFormatter.ofPattern("MMM yyyy"));

        return PlTrendsResponse.builder()
                .periodLabel(periodLabel)
                .branches(trends)
                .build();
    }

    private Map<UUID, Map<LocalDate, BigDecimal>> aggregateRevenueByBranchMonth(List<Invoice> invoices) {
        Map<UUID, Map<LocalDate, BigDecimal>> result = new HashMap<>();
        for (Invoice inv : invoices) {
            LocalDate month = inv.getIssuedAt().atZone(ZONE).toLocalDate().withDayOfMonth(1);
            result.computeIfAbsent(inv.getBranchId(), k -> new HashMap<>())
                    .merge(month, inv.getGrandTotal(), BigDecimal::add);
        }
        return result;
    }

    private Map<UUID, Map<LocalDate, BigDecimal>> aggregateExpensesByBranchMonth(List<BranchExpenditure> expenditures) {
        Map<UUID, Map<LocalDate, BigDecimal>> result = new HashMap<>();
        for (BranchExpenditure e : expenditures) {
            result.computeIfAbsent(e.getBranchId(), k -> new HashMap<>())
                    .merge(e.getExpenseMonth(), e.getAmount(), BigDecimal::add);
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

    private List<PlCategoryAmount> buildCategoryList(Map<ExpenditureCategory, BigDecimal> catMap) {
        return Arrays.stream(ExpenditureCategory.values())
                .map(cat -> PlCategoryAmount.builder()
                        .category(cat)
                        .amount(catMap.getOrDefault(cat, BigDecimal.ZERO))
                        .build())
                .filter(c -> c.getAmount().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());
    }

    private BigDecimal marginPercent(BigDecimal revenue, BigDecimal netProfit) {
        if (revenue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return netProfit.multiply(BigDecimal.valueOf(100))
                .divide(revenue, 1, RoundingMode.HALF_UP);
    }
}
