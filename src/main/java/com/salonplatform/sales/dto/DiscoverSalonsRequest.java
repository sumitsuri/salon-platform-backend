package com.salonplatform.sales.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class DiscoverSalonsRequest {

    @NotNull
    private UUID localityId;

    @Min(1)
    @Max(15)
    private int radiusKm = 3;

    /** Admin only — assign imported leads to this rep. Sales exec always self. */
    private UUID assignRepId;
}
