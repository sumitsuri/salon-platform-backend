package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BranchPlSummary {
    private UUID branchId;
    private String branchName;
    private BigDecimal revenue;
    private List<PlCategoryAmount> expensesByCategory;
    private BigDecimal totalExpenses;
    private BigDecimal netProfit;
    private BigDecimal marginPercent;
}
