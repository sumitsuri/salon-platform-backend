package com.salonplatform.dto.expenditure;

import com.salonplatform.domain.enums.ExpenditureCategory;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class UpdateExpenditureRequest {
    private UUID branchId;
    private ExpenditureCategory category;
    private LocalDate expenseMonth;
    private BigDecimal amount;
    private String description;
}
