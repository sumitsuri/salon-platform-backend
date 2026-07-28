package com.salonplatform.dto.catalog;

import lombok.Data;

import java.util.UUID;

@Data
public class UpdateCategoryRequest {
    private String name;
    private Integer sortOrder;
    private UUID parentCategoryId;
    private Boolean clearParent;
    private Boolean active;
}
