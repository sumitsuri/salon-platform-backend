package com.salonplatform.dto.inventory;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class BranchInventorySummary {
    private UUID branchId;
    private String branchName;
    private BigDecimal productCost;
    private BigDecimal stockValue;
    private int lowStockCount;
    private int movementCount;
}
