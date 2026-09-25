package com.salonplatform.sales.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class FieldLocationPingResponse {
    private double latitude;
    private double longitude;
    private Double accuracyMeters;
    private Instant capturedAt;
}
