package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class PlTrendPoint {
    private LocalDate month;
    private BigDecimal revenue;
    private BigDecimal totalExpenses;
    private BigDecimal netProfit;
    private BigDecimal marginPercent;
}
