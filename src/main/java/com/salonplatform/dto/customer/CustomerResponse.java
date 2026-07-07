package com.salonplatform.dto.customer;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CustomerResponse {
    private UUID id;
    private String name;
    private String phone;
    private String society;
    private String flatUnit;
    private String notes;
    private Integer visitCount;
    private BigDecimal lifetimeSpend;
    private Instant lastVisitAt;
}
