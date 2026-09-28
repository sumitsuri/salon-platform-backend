package com.salonplatform.dto.booking;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class RescheduleBookingRequest {
    @NotNull
    private Instant scheduledStartAt;
    @NotNull
    private Instant scheduledEndAt;
    /** New stylist, when the booking has no service lines to reassign instead. */
    private UUID staffId;
}
