package com.salonplatform.dto.catalog;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class ServiceBranchAssignmentResponse {
    private UUID branchServiceId;
    private UUID branchId;
    private String branchName;
    private BigDecimal price;
    private String displayNameOverride;
    private boolean active;
    private boolean manualPriceOverride;
}
