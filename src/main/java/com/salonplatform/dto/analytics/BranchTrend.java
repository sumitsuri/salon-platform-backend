package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BranchTrend {
    private UUID branchId;
    private String branchName;
    private List<TrendPoint> points;
    private BigDecimal revenueChangePct;
    private BigDecimal visitsChangePct;
    private BigDecimal avgTicketChangePct;
    private BigDecimal discountsChangePct;
}
