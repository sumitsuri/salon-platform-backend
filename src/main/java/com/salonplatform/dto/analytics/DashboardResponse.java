package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class DashboardResponse {
    private BigDecimal totalRevenue;
    private long totalVisits;
    private BigDecimal avgTicketSize;
    private BigDecimal totalDiscounts;
    private List<BranchStats> branchStats;
    private List<BranchTrend> branchTrends;
    private List<ServiceStats> topServices;
    private List<StaffStats> topStaff;
    private PaymentMix paymentMix;
}
