package com.salonplatform.dto.catalog;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class BranchPricingRequest {
    @NotNull
    private UUID serviceId;
    @NotNull
    private BigDecimal price;
    private String displayNameOverride;
}
