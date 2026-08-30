package com.salonplatform.dto.staff;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class StaffTargetPerformanceItem {
    private UUID staffId;
    private String staffName;
    private UUID branchId;
    private String branchName;
    private BigDecimal monthlySalesTarget;
    private BigDecimal actualSales;
    /** Total services performed by this staff in the period (sum of line item quantities). */
    private long salesCount;
    private BigDecimal avgTicketSize;
    private BigDecimal achievementPercent;
    private boolean meetingTarget;
    private boolean onTrack;
    private BigDecimal incentivePercent;
    private BigDecimal projectedIncentive;
}
