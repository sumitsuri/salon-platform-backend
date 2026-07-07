package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PaymentMix {
    private BigDecimal cash;
    private BigDecimal upi;
    private BigDecimal card;
}
