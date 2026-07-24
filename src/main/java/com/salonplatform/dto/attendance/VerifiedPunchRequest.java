package com.salonplatform.dto.attendance;

import lombok.Data;

import java.util.UUID;

@Data
public class VerifiedPunchRequest {
    private UUID staffId;
    /** CHECK_IN or CHECK_OUT — inferred if omitted (toggle like biometric). */
    private String action;
    private Double latitude;
    private Double longitude;
    private Double accuracyMeters;
    /** True when fix came from device GPS; false for network/Wi‑Fi location. */
    private Boolean locationHighAccuracy;
}
