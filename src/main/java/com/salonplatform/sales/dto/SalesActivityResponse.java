package com.salonplatform.sales.dto;

import com.salonplatform.sales.domain.enums.ActivityType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class SalesActivityResponse {
    private UUID id;
    private UUID leadId;
    private UUID repId;
    private String repName;
    private ActivityType activityType;
    private String notes;
    private Instant activityAt;
    private Instant createdAt;
}
