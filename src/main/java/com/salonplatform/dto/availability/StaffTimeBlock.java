package com.salonplatform.dto.availability;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class StaffTimeBlock {
    private UUID bookingId;
    private UUID customerId;
    private String customerName;
    private String customerPhone;
    private String status;
    private Instant startAt;
    private Instant endAt;
    private Integer estimatedMinutes;
    private Integer actualMinutes;
    private boolean overdue;
    private List<String> services;
    /** WALK_IN or ONLINE — used on floor schedule to highlight web bookings. */
    private String source;
}
