package com.salonplatform.dto.inventory;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BranchInventoryTrend {
    private UUID branchId;
    private String branchName;
    private List<InventoryTrendPoint> points;
    private BigDecimal costChangePct;
}
