package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ServiceContributionItem {
    private String serviceName;
    private BigDecimal revenue;
    private long count;
    private double revenueSharePct;
    private double countSharePct;
}
