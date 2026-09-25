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
    /** True when the latest ping is within the server active window (field mode recently on). */
    private boolean active;
    /** False when the rep has never sent a location ping. */
    private boolean hasLocation;
    private Double latitude;
    private Double longitude;
    private Double accuracyMeters;
    private Instant capturedAt;
    private Long secondsSinceLastPing;
}
