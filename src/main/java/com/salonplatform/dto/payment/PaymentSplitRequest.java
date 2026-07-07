package com.salonplatform.dto.payment;

import com.salonplatform.domain.enums.PaymentMode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentSplitRequest {
    @NotNull
    private PaymentMode mode;
    @NotNull
    private BigDecimal amount;
    private String reference;
}
