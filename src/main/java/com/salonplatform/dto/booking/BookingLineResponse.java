package com.salonplatform.dto.booking;

import com.salonplatform.domain.enums.DiscountType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class BookingLineResponse {
    private UUID id;
    private UUID branchServiceId;
    private UUID serviceId;
    private UUID staffId;
    private String staffName;
    private String serviceName;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal gstRate;
    private DiscountType lineDiscountType;
    private BigDecimal lineDiscountValue;
}
