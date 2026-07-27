package com.salonplatform.sales.dto;

import com.salonplatform.sales.domain.enums.LeadType;
import com.salonplatform.sales.domain.enums.BillingPeriod;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class UpdateSalesLeadRequest {

    private UUID localityId;

    private String localityName;

    private LeadType leadType;

    /** Comma-separated selected use cases. Custom use cases go in notes. */
    private String useCase;

    private String notes;

    private Integer expectedBranches;

    private BigDecimal quotedAmount;

    private BillingPeriod billingPeriod;

    private BigDecimal discountPercent;

    private BigDecimal discountAmount;

    private BigDecimal finalPaidAmount;
}
