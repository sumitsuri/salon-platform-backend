package com.salonplatform.dto.inventory;

import com.salonplatform.domain.enums.InventoryUnit;
import com.salonplatform.domain.enums.ProductCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class StockItemResponse {
    private UUID id;
    private UUID branchId;
    private String branchName;
    private UUID productId;
    private String productName;
    private String sku;
    private ProductCategory category;
    private InventoryUnit unit;
    private UUID vendorId;
    private String vendorName;
    private BigDecimal quantity;
    private BigDecimal reorderLevel;
    private BigDecimal unitCost;
    private BigDecimal stockValue;
    private boolean lowStock;
    private boolean outOfStock;
}
