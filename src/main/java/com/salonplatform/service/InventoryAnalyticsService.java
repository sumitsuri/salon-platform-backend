package com.salonplatform.service;

import com.salonplatform.domain.entity.*;
import com.salonplatform.domain.enums.MovementType;
import com.salonplatform.domain.repository.*;
import com.salonplatform.dto.inventory.*;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryAnalyticsService {

    private static final List<MovementType> COST_TYPES = List.of(
            MovementType.RESTOCK, MovementType.USAGE, MovementType.WASTAGE, MovementType.RETAIL_SALE);
    private static final int DEFAULT_TREND_MONTHS = 6;

    private final BranchRepository branchRepository;
    private final BranchInventoryRepository stockRepository;
    private final InventoryMovementRepository movementRepository;
    private final InventoryProductRepository productRepository;

    public InventoryOverviewResponse getOverview(LocalDate month, List<UUID> branchIds) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        LocalDate m = (month != null ? month : LocalDate.now()).withDayOfMonth(1);
        LocalDate monthEnd = m.withDayOfMonth(m.lengthOfMonth());

        List<Branch> branches = branchRepository.findByTenantId(tenantId).stream()
                .filter(b -> branchIds == null || branchIds.isEmpty() || branchIds.contains(b.getId()))
                .collect(Collectors.toList());

        List<BranchInventory> allStock = stockRepository.findByTenantId(tenantId).stream()
                .filter(s -> branchIds == null || branchIds.isEmpty() || branchIds.contains(s.getBranchId()))
                .collect(Collectors.toList());

        Map<UUID, InventoryProduct> products = productRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId)
                .stream().collect(Collectors.toMap(InventoryProduct::getId, p -> p));

        List<InventoryMovement> movements = movementRepository
                .findByTenantIdAndMovementDateBetweenOrderByMovementDateDescCreatedAtDesc(tenantId, m, monthEnd);
        if (branchIds != null && !branchIds.isEmpty()) {
            Set<UUID> set = new HashSet<>(branchIds);
            movements = movements.stream().filter(mv -> set.contains(mv.getBranchId())).collect(Collectors.toList());
        }

        BigDecimal totalProductCost = movements.stream()
                .filter(mv -> COST_TYPES.contains(mv.getMovementType()))
                .map(InventoryMovement::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalStockValue = BigDecimal.ZERO;
        int lowStock = 0;
        int outOfStock = 0;
        for (BranchInventory s : allStock) {
            InventoryProduct p = products.get(s.getProductId());
            if (p == null) continue;
            totalStockValue = totalStockValue.add(s.getQuantity().multiply(p.getUnitCost()));
            if (s.getQuantity().compareTo(BigDecimal.ZERO) <= 0) outOfStock++;
            else if (p.getReorderLevel() != null && p.getReorderLevel().compareTo(BigDecimal.ZERO) > 0
                    && s.getQuantity().compareTo(p.getReorderLevel()) <= 0) lowStock++;
        }
        totalStockValue = totalStockValue.setScale(2, RoundingMode.HALF_UP);

        Map<UUID, BigDecimal> costByProduct = new HashMap<>();
        for (InventoryMovement mv : movements) {
            if (COST_TYPES.contains(mv.getMovementType())) {
                costByProduct.merge(mv.getProductId(), mv.getTotalCost(), BigDecimal::add);
            }
        }
        String topProductName = null;
        BigDecimal topProductAmount = BigDecimal.ZERO;
        for (Map.Entry<UUID, BigDecimal> e : costByProduct.entrySet()) {
            if (e.getValue().compareTo(topProductAmount) > 0) {
                topProductAmount = e.getValue();
                topProductName = products.containsKey(e.getKey()) ? products.get(e.getKey()).getName() : "—";
            }
        }

        List<BranchInventorySummary> branchSummaries = new ArrayList<>();
        for (Branch branch : branches) {
            UUID bid = branch.getId();
            BigDecimal branchCost = movements.stream()
                    .filter(mv -> mv.getBranchId().equals(bid) && COST_TYPES.contains(mv.getMovementType()))
                    .map(InventoryMovement::getTotalCost)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal branchStockValue = allStock.stream()
                    .filter(s -> s.getBranchId().equals(bid))
                    .map(s -> {
                        InventoryProduct p = products.get(s.getProductId());
                        return p != null ? s.getQuantity().multiply(p.getUnitCost()) : BigDecimal.ZERO;
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            int branchLow = 0;
            for (BranchInventory s : allStock) {
                if (!s.getBranchId().equals(bid)) continue;
                InventoryProduct p = products.get(s.getProductId());
                if (p == null) continue;
                if (s.getQuantity().compareTo(BigDecimal.ZERO) > 0 && p.getReorderLevel() != null
                        && s.getQuantity().compareTo(p.getReorderLevel()) <= 0) branchLow++;
            }
            long movementCount = movements.stream().filter(mv -> mv.getBranchId().equals(bid)).count();
            branchSummaries.add(BranchInventorySummary.builder()
                    .branchId(bid)
                    .branchName(branch.getName())
                    .productCost(branchCost)
                    .stockValue(branchStockValue)
                    .lowStockCount(branchLow)
                    .movementCount((int) movementCount)
                    .build());
        }

        String periodLabel = m.format(DateTimeFormatter.ofPattern("MMM yyyy"));

        return InventoryOverviewResponse.builder()
                .periodLabel(periodLabel)
                .totalProductCost(totalProductCost)
                .totalStockValue(totalStockValue)
                .lowStockCount(lowStock)
                .outOfStockCount(outOfStock)
                .topCostProductName(topProductName)
                .topCostProductAmount(topProductAmount)
                .branches(branchSummaries)
                .build();
    }

    public InventoryTrendsResponse getTrends(LocalDate endMonth, Integer months, List<UUID> branchIds) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();

        LocalDate end = (endMonth != null ? endMonth : LocalDate.now()).withDayOfMonth(1);
        int monthCount = months != null && months > 0 ? months : DEFAULT_TREND_MONTHS;
        LocalDate start = end.minusMonths(monthCount - 1L).withDayOfMonth(1);
        LocalDate rangeEnd = end.withDayOfMonth(end.lengthOfMonth());

        List<Branch> branches = branchRepository.findByTenantId(tenantId).stream()
                .filter(b -> branchIds == null || branchIds.isEmpty() || branchIds.contains(b.getId()))
                .collect(Collectors.toList());

        List<InventoryMovement> movements = movementRepository
                .findByTenantIdAndMovementDateBetweenOrderByMovementDateDescCreatedAtDesc(tenantId, start, rangeEnd);
        if (branchIds != null && !branchIds.isEmpty()) {
            Set<UUID> set = new HashSet<>(branchIds);
            movements = movements.stream().filter(mv -> set.contains(mv.getBranchId())).collect(Collectors.toList());
        }

        List<BranchInventory> allStock = stockRepository.findByTenantId(tenantId);
        Map<UUID, InventoryProduct> products = productRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId)
                .stream().collect(Collectors.toMap(InventoryProduct::getId, p -> p));

        List<YearMonth> monthPoints = new ArrayList<>();
        for (LocalDate m = start; !m.isAfter(end); m = m.plusMonths(1)) {
            monthPoints.add(YearMonth.from(m));
        }

        List<BranchInventoryTrend> trends = new ArrayList<>();
        for (Branch branch : branches) {
            List<InventoryTrendPoint> points = new ArrayList<>();
            List<BigDecimal> costs = new ArrayList<>();

            for (YearMonth ym : monthPoints) {
                LocalDate mStart = ym.atDay(1);
                LocalDate mEnd = ym.atEndOfMonth();
                UUID bid = branch.getId();

                BigDecimal productCost = movements.stream()
                        .filter(mv -> mv.getBranchId().equals(bid)
                                && !mv.getMovementDate().isBefore(mStart)
                                && !mv.getMovementDate().isAfter(mEnd)
                                && COST_TYPES.contains(mv.getMovementType()))
                        .map(InventoryMovement::getTotalCost)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal usageCost = movements.stream()
                        .filter(mv -> mv.getBranchId().equals(bid)
                                && !mv.getMovementDate().isBefore(mStart)
                                && !mv.getMovementDate().isAfter(mEnd)
                                && mv.getMovementType() == MovementType.USAGE)
                        .map(InventoryMovement::getTotalCost)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal wastageCost = movements.stream()
                        .filter(mv -> mv.getBranchId().equals(bid)
                                && !mv.getMovementDate().isBefore(mStart)
                                && !mv.getMovementDate().isAfter(mEnd)
                                && mv.getMovementType() == MovementType.WASTAGE)
                        .map(InventoryMovement::getTotalCost)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal stockValue = allStock.stream()
                        .filter(s -> s.getBranchId().equals(bid))
                        .map(s -> {
                            InventoryProduct p = products.get(s.getProductId());
                            return p != null ? s.getQuantity().multiply(p.getUnitCost()) : BigDecimal.ZERO;
                        })
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_UP);

                points.add(InventoryTrendPoint.builder()
                        .month(mStart)
                        .productCost(productCost)
                        .stockValue(stockValue)
                        .usageCost(usageCost)
                        .wastageCost(wastageCost)
                        .build());
                costs.add(productCost);
            }

            trends.add(BranchInventoryTrend.builder()
                    .branchId(branch.getId())
                    .branchName(branch.getName())
                    .points(points)
                    .costChangePct(changePctFromSeries(costs))
                    .build());
        }

        String periodLabel = start.format(DateTimeFormatter.ofPattern("MMM yyyy")) + " – "
                + end.format(DateTimeFormatter.ofPattern("MMM yyyy"));

        return InventoryTrendsResponse.builder()
                .periodLabel(periodLabel)
                .branches(trends)
                .build();
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
