package com.salonplatform.dto.branch;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class BranchTargetPerformanceItem {
    private UUID branchId;
    private String branchName;
    private BigDecimal monthlySalesTarget;
    private BigDecimal actualSales;
    private BigDecimal achievementPercent;
    private boolean meetingTarget;
    private boolean onTrack;
}
