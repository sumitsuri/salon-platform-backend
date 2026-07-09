package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BranchPlTrend {
    private UUID branchId;
    private String branchName;
    private List<PlTrendPoint> points;
    private BigDecimal revenueChangePct;
    private BigDecimal expensesChangePct;
    private BigDecimal netProfitChangePct;
    private BigDecimal marginChangePct;
}
