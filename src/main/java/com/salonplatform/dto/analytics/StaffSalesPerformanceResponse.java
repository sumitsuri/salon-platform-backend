package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class StaffSalesPerformanceResponse {
    private List<StaffSalesRow> staff;

    @Data
    @Builder
    public static class StaffSalesRow {
        private UUID staffId;
        private String staffName;
        private UUID branchId;
        private String branchName;
        /** Sum of line quantities. */
        private long salesCount;
        /** Catalog / list total before discounts. */
        private BigDecimal listRevenue;
        /** Final collected (post-discount, incl. line GST). */
        private BigDecimal finalRevenue;
        private BigDecimal avgFinalTicket;
    }
}
