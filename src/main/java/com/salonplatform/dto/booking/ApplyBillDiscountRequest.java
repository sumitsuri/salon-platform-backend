package com.salonplatform.dto.booking;

import com.salonplatform.domain.enums.DiscountType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ApplyBillDiscountRequest {
    private DiscountType billDiscountType;
    private BigDecimal billDiscountValue;
    private String billDiscountNote;
    /** When true, clears manager bill discount. */
    private Boolean clearDiscount;
}
