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
