package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class TrendPoint {
    private LocalDate date;
    private BigDecimal revenue;
    private long visits;
    private BigDecimal avgTicket;
    private BigDecimal discounts;
}
