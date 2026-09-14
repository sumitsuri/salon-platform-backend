package com.salonplatform.dto.booking;

import lombok.Data;

import java.util.UUID;

@Data
public class SetPendingPackagePlanRequest {
    private UUID planId;
    private UUID soldByStaffId;
}
