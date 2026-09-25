package com.salonplatform.sales.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ActiveFieldRepResponse {
    private UUID repId;
    private String repName;
    private double latitude;
    private double longitude;
    private Double accuracyMeters;
    private Instant capturedAt;
    private long secondsSinceLastPing;
}
