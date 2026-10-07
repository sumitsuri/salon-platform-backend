package com.salonplatform.dto.staffportal;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class StaffPortalSalesInsightsResponse {
    private String historyFilterLabel;
    private LocalDate historyFrom;
    private LocalDate historyTo;
    private List<StaffSaleHistoryLine> history;
    private PeriodSummary periodSummary;
    private List<ServiceContribution> serviceContributions;
    private String focusSummary;
    private StaffSalesBoostSection boost;

    @Data
    @Builder
    public static class PeriodSummary {
        private long serviceCount;
        private BigDecimal totalSales;
        private BigDecimal avgTicket;
    }

    @Data
    @Builder
    public static class ServiceContribution {
        private String serviceName;
        private long count;
        private BigDecimal revenue;
        private BigDecimal sharePercent;
    }

    @Data
    @Builder
    public static class StaffSaleHistoryLine {
        private LocalDate serviceDate;
        private String serviceName;
        private int quantity;
        private BigDecimal amount;
    }

    @Data
    @Builder
    public static class StaffSalesBoostSection {
        private BigDecimal monthlyTarget;
        private BigDecimal actualSalesMtd;
        private BigDecimal gapToTarget;
        private int daysRemaining;
        private BigDecimal dailyNeeded;
        private String trackLabel;
        private List<StaffSalesBoostSuggestion> suggestions;
    }

    @Data
    @Builder
    public static class StaffSalesBoostSuggestion {
        private String serviceName;
        private BigDecimal typicalAmount;
        private int suggestedCount;
        private BigDecimal estimatedRevenue;
        private String rationale;
    }
}
