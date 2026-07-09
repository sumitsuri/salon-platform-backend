package com.salonplatform.dto.inventory;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class InventoryTrendsResponse {
    private String periodLabel;
    private List<BranchInventoryTrend> branches;
}
