package com.salonplatform.dto.catalog;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class BranchServiceResponse {
    private UUID id;
    private UUID branchId;
    private UUID serviceId;
    private String serviceName;
    private String categoryName;
    private UUID categoryId;
    private BigDecimal price;
    private BigDecimal gstRate;
    private String displayNameOverride;
    private boolean active;
}
