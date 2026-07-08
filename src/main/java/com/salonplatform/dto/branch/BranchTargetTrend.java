package com.salonplatform.dto.branch;

import com.salonplatform.dto.staff.StaffTargetTrendPoint;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BranchTargetTrend {
    private UUID branchId;
    private String branchName;
    private BigDecimal monthlySalesTarget;
    private List<StaffTargetTrendPoint> points;
    private BigDecimal actualChangePct;
    private BigDecimal gapChangePct;
}
