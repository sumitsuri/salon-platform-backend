package com.salonplatform.sales.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

@Data
public class CreateFieldLocationPingRequest {

    @NotNull
    private Double latitude;

    @NotNull
    private Double longitude;

    private Double accuracyMeters;

    @NotNull
    private Instant capturedAt;
}
