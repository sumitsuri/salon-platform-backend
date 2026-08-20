package com.salonplatform.dto.branch;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BulkOnlineBookingRequest {
    private Boolean enabled;
    /** When empty or null, applies to all active branches for the brand. */
    private List<UUID> branchIds;
}
