package com.salonplatform.dto.billing;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BillLinePreview {
    private UUID lineItemId;
    private String serviceName;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineDiscount;
    private BigDecimal taxableAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal lineTotal;
}
