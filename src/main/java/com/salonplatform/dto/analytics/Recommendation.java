package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class Recommendation {
    private String id;
    private String category;
    private String severity;
    private String title;
    private String message;
    private UUID branchId;
    private String branchName;
    private String metricLabel;
    private String metricValue;
}
