package com.salonplatform.dto.inventory;

import com.salonplatform.domain.enums.InventoryUnit;
import com.salonplatform.domain.enums.ProductCategory;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class UpdateProductRequest {
    private UUID vendorId;
    private String name;
    private String sku;
    private ProductCategory category;
    private InventoryUnit unit;
    private BigDecimal unitCost;
    private BigDecimal retailPrice;
    private BigDecimal reorderLevel;
}
