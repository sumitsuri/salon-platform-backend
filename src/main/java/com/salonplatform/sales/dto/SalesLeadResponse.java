package com.salonplatform.sales.dto;

import com.salonplatform.sales.domain.enums.LeadSource;
import com.salonplatform.sales.domain.enums.LeadStage;
import com.salonplatform.sales.domain.enums.LeadType;
import com.salonplatform.sales.domain.enums.BillingPeriod;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class SalesLeadResponse {
    private UUID id;
    private String businessName;
    private String contactName;
    private String email;
    private String phone;
    private LeadType leadType;
    private LeadStage stage;
    private LeadSource source;
    private UUID localityId;
    private String localityName;
    private String address;
    private String city;
    private int expectedBranches;
    private String useCase;
    private String notes;
    private UUID assignedRepId;
    private String assignedRepName;
    private UUID convertedTenantId;
    private BigDecimal projectedMrr;
    private String planTier;
    private BigDecimal quotedAmount;
    private BillingPeriod billingPeriod;
    private BigDecimal discountPercent;
    private BigDecimal discountAmount;
    private BigDecimal finalPaidAmount;
    private String lostReason;
    private Instant trialIntentAt;
    private Instant convertedAt;
    private Instant nextFollowUpAt;
    private Instant createdAt;
    private Instant updatedAt;
}
