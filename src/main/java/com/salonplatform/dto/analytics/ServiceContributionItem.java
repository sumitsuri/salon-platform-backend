package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ServiceContributionItem {
    private String serviceName;
    /** Catalog / list price total (before discounts). */
    private BigDecimal listRevenue;
    /** Final collected amount per line (after discounts, incl. line GST). */
    private BigDecimal revenue;
    private long count;
    private double revenueSharePct;
    private double countSharePct;
}
