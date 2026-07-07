package com.salonplatform.dto.booking;

import com.salonplatform.domain.enums.DiscountType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class BookingLineRequest {
    @NotNull
    private UUID branchServiceId;
    @NotNull
    private UUID staffId;
    private Integer quantity = 1;
    private DiscountType lineDiscountType;
    private BigDecimal lineDiscountValue;
    private String lineDiscountNote;
}
