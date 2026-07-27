package com.salonplatform.sales.dto;

import com.salonplatform.sales.domain.enums.IncentiveEventType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpsertIncentiveRuleRequest {
    @NotNull
    private IncentiveEventType eventType;
    @NotNull
    private BigDecimal amountInr;
    private boolean active = true;
}
