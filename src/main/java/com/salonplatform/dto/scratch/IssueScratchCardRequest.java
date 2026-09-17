package com.salonplatform.dto.scratch;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class IssueScratchCardRequest {
    @NotNull
    private UUID campaignId;
    @NotNull
    private UUID branchId;
    private UUID bookingId;
}
