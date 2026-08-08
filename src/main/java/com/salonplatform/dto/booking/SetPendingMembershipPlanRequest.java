package com.salonplatform.dto.booking;

import lombok.Data;

import java.util.UUID;

@Data
public class SetPendingMembershipPlanRequest {
    /** Null clears a pending membership add-on for this visit. */
    private UUID planId;
}
