package com.salonplatform.dto.inventory;

import com.salonplatform.domain.enums.InventoryUnit;
import com.salonplatform.domain.enums.ProductCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateProductRequest {
    @NotNull
    private UUID vendorId;
    @NotBlank
    private String name;
    private String sku;
    @NotNull
    private ProductCategory category;
    @NotNull
    private InventoryUnit unit;
    @NotNull
    private BigDecimal unitCost;
    private BigDecimal retailPrice;
    private BigDecimal reorderLevel;
}
