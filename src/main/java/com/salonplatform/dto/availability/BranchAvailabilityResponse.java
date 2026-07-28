package com.salonplatform.dto.availability;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BranchAvailabilityResponse {
    private UUID branchId;
    private String branchName;
    private LocalDate date;
    private String openTime;
    private String closeTime;
    private Instant now;
    private int freeStaffCount;
    private int busyStaffCount;
    private List<StaffAvailabilityColumn> staff;
    private DurationMetrics metrics;
}
