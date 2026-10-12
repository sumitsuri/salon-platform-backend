package com.salonplatform.dto.advance;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class MyAdvancesResponse {
    private AdvanceEligibilityResponse eligibility;
    private List<AdvanceResponse> advances;
    private int requestCount;
    private BigDecimal totalRequested;
    private BigDecimal totalApproved;
    private BigDecimal outstanding;
    /** The next installment still to come off salary, if any. */
    private AdvanceInstallmentResponse nextInstallment;
}
