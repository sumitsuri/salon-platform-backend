package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class BrandPlSummary {
    private BigDecimal revenue;
    private List<PlCategoryAmount> expensesByCategory;
    private BigDecimal totalExpenses;
    private BigDecimal netProfit;
    private BigDecimal marginPercent;
}
