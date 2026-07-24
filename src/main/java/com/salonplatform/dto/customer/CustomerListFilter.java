package com.salonplatform.dto.customer;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class CustomerListFilter {
    private String name;
    private String society;
    private String phone;
    private Integer minVisitCount;
    private Integer maxVisitCount;
    private BigDecimal minLifetimeSpend;
    private BigDecimal maxLifetimeSpend;
    private LocalDate lastVisitFrom;
    private LocalDate lastVisitTo;
    private Boolean whatsappOptInOnly;
    private Boolean smsOptInOnly;
    private int page;
    private int size;
}
