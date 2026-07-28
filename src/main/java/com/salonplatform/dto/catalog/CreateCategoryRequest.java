package com.salonplatform.dto.catalog;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateCategoryRequest {
    @NotBlank
    private String name;
    private Integer sortOrder;
    /** Optional parent for Men/Women/Kids → sub-category hierarchy. */
    private UUID parentCategoryId;
}
