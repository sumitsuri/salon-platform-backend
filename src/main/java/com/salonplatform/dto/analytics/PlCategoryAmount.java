package com.salonplatform.dto.analytics;

import com.salonplatform.domain.enums.ExpenditureCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PlCategoryAmount {
    private ExpenditureCategory category;
    private BigDecimal amount;
}
