package com.salonplatform.dto.packageplan;

import com.salonplatform.domain.enums.PackagePlanType;
import com.salonplatform.domain.enums.PackageRedemptionMode;
import com.salonplatform.domain.enums.PackageSubscriptionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CustomerPackageSubscriptionResponse {
    private UUID id;
    private UUID customerId;
    private String customerName;
    private String customerPhone;
    private UUID branchId;
    private String branchName;
    private UUID planId;
    private String planName;
    private PackagePlanType planType;
    private BigDecimal creditTotal;
    private BigDecimal creditRemaining;
    private PackageRedemptionMode redemptionMode;
    private BigDecimal amountPaid;
    private LocalDate purchasedOn;
    private LocalDate expiresOn;
    private PackageSubscriptionStatus status;
    private List<CustomerPackageEntitlementResponse> entitlements;
    private Instant createdAt;
}
