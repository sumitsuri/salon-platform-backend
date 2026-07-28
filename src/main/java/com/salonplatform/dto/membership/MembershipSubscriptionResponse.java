package com.salonplatform.dto.membership;

import com.salonplatform.domain.enums.MembershipStatus;
import com.salonplatform.domain.enums.PaymentMode;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class MembershipSubscriptionResponse {
    private UUID id;
    private UUID customerId;
    private String customerName;
    private String customerPhone;
    private UUID planId;
    private String planName;
    private BigDecimal benefitPercent;
    private UUID branchId;
    private String branchName;
    private String cardNumber;
    private LocalDate startsOn;
    private LocalDate endsOn;
    private MembershipStatus status;
    private BigDecimal amountPaid;
    private PaymentMode paymentMode;
    private String paymentReference;
    private Instant createdAt;
}
