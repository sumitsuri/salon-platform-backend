package com.salonplatform.sales.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class PipelineAnalyticsResponse {
    private Map<String, Long> stageCounts;
    private long totalOpen;
    private long totalWon;
    private long totalLost;
}
