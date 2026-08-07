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
    /** Leaf category (e.g. Hair Cut & Styling). */
    private UUID categoryId;
    private String categoryName;
    /** Top category (Men / Women / Kids / Shared). */
    private UUID parentCategoryId;
    private String parentCategoryName;
    private BigDecimal price;
    private BigDecimal gstRate;
    private Integer durationMinutes;
    private boolean variablePricing;
    private String displayNameOverride;
    private boolean active;
}
