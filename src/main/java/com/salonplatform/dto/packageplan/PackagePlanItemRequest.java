package com.salonplatform.dto.packageplan;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class PackagePlanItemRequest {
    @NotNull
    private UUID serviceId;
    @Min(1)
    private Integer quantity = 1;
    private Integer sortOrder;
}
