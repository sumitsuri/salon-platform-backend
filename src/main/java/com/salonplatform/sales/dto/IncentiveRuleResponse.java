package com.salonplatform.sales.dto;

import com.salonplatform.sales.domain.enums.IncentiveEventType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class IncentiveRuleResponse {
    private UUID id;
    private IncentiveEventType eventType;
    private BigDecimal amountInr;
    private boolean active;
}
