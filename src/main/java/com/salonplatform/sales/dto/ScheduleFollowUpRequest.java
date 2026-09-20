package com.salonplatform.sales.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

@Data
public class ScheduleFollowUpRequest {

    @NotNull
    private Instant followUpAt;

    private String notes;
}
