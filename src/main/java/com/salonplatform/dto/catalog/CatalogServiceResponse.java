package com.salonplatform.dto.catalog;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CatalogServiceResponse {
    private UUID id;
    private String name;
    private String description;
    private UUID categoryId;
    private String categoryName;
    private UUID parentCategoryId;
    private String parentCategoryName;
    private String sacCode;
    private BigDecimal gstRate;
    private Integer durationMinutes;
    private boolean active;
    private BigDecimal listPrice;
    @Builder.Default
    private List<ServiceBranchAssignmentResponse> branches = new ArrayList<>();
}
