package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class StaffPromoSalesResponse {
    private BigDecimal membershipIncentivePerSale;
    /** Percent of package sale amount (e.g. 3). */
    private BigDecimal packageIncentivePercent;
    private List<StaffPromoSalesRow> staff;

    @Data
    @Builder
    public static class StaffPromoSalesRow {
        private UUID staffId;
        private String staffName;
        private int membershipCountToday;
        private int packageCountToday;
        private int membershipCountTotal;
        private int packageCountTotal;
        private BigDecimal membershipTodayEarnings;
        private BigDecimal membershipTotalEarnings;
        private BigDecimal packageTodayEarnings;
        private BigDecimal packageTotalEarnings;
        /** Combined (membership + package) for sorting. */
        private BigDecimal todayEarnings;
        private BigDecimal totalEarnings;
    }
}
