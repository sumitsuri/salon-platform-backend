package com.salonplatform.dto.membership;

import com.salonplatform.domain.enums.PaymentMode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class SellMembershipRequest {
    @NotNull
    private UUID customerId;
    @NotNull
    private UUID planId;
    @NotNull
    private UUID branchId;
    @NotNull
    private PaymentMode paymentMode;
    private String paymentReference;
    /** Optional override; defaults to plan fee. */
    private BigDecimal amount;
}
