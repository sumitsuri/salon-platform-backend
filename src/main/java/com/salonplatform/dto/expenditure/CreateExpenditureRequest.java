package com.salonplatform.dto.expenditure;

import com.salonplatform.domain.enums.ExpenditureCategory;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateExpenditureRequest {
    @NotNull
    private UUID branchId;
    @NotNull
    private ExpenditureCategory category;
    @NotNull
    private LocalDate expenseMonth;
    @NotNull
    private BigDecimal amount;
    private String description;
}
