package com.salonplatform.dto.inventory;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class InventoryTrendPoint {
    private LocalDate month;
    private BigDecimal productCost;
    private BigDecimal stockValue;
    private BigDecimal usageCost;
    private BigDecimal wastageCost;
}
