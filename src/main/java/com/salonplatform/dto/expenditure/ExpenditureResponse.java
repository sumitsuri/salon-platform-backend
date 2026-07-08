package com.salonplatform.dto.expenditure;

import com.salonplatform.domain.enums.ExpenditureCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class ExpenditureResponse {
    private UUID id;
    private UUID branchId;
    private String branchName;
    private ExpenditureCategory category;
    private LocalDate expenseMonth;
    private BigDecimal amount;
    private String description;
    private Instant createdAt;
}
