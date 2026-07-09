package com.salonplatform.dto.inventory;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class InventoryOverviewResponse {
    private String periodLabel;
    private BigDecimal totalProductCost;
    private BigDecimal totalStockValue;
    private int lowStockCount;
    private int outOfStockCount;
    private String topCostProductName;
    private BigDecimal topCostProductAmount;
    private List<BranchInventorySummary> branches;
}
