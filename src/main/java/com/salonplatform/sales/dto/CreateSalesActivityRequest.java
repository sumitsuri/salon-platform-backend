package com.salonplatform.sales.dto;

import com.salonplatform.sales.domain.enums.ActivityType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

@Data
public class CreateSalesActivityRequest {

    @NotNull
    private ActivityType activityType;

    private String notes;

    private Instant activityAt;
}
