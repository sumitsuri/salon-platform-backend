package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ServiceContributionResponse {
    private BigDecimal totalRevenue;
    private BigDecimal serviceRevenue;
    private long totalServiceCount;
    private List<ServiceContributionItem> services;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
