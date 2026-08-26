package com.salonplatform.dto.customer;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class CustomerListFilter {
    private String name;
    private List<String> names;
    private String society;
    private String phone;
    private List<String> phones;
    private String visitPassId;
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
