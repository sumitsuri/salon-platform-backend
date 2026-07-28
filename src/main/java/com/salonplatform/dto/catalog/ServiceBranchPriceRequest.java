package com.salonplatform.dto.catalog;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class ServiceBranchPriceRequest {
    @NotNull
    private UUID branchId;
    @NotNull
    private BigDecimal price;
    private String displayNameOverride;
    private Boolean active = true;
}
