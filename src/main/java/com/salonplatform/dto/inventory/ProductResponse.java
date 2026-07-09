package com.salonplatform.dto.inventory;

import com.salonplatform.domain.enums.InventoryUnit;
import com.salonplatform.domain.enums.ProductCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ProductResponse {
    private UUID id;
    private UUID vendorId;
    private String vendorName;
    private String name;
    private String sku;
    private ProductCategory category;
    private InventoryUnit unit;
    private BigDecimal unitCost;
    private BigDecimal retailPrice;
    private BigDecimal reorderLevel;
    private Instant createdAt;
}
