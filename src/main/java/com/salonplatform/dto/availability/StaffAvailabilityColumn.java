package com.salonplatform.dto.availability;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class StaffAvailabilityColumn {
    private UUID staffId;
    private String staffName;
    private String skills;
    /** FREE | BUSY | OVERDUE */
    private String occupancy;
    private Instant busyUntil;
    private Integer remainingMinutes;
    private List<StaffTimeBlock> blocks;
    private List<FreeSlot> freeSlots;
}
