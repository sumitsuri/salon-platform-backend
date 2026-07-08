package com.salonplatform.dto.staff;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class StaffTargetTrend {
    private UUID staffId;
    private String staffName;
    private UUID branchId;
    private String branchName;
    private BigDecimal monthlySalesTarget;
    private List<StaffTargetTrendPoint> points;
    private BigDecimal actualChangePct;
    private BigDecimal gapChangePct;
}
