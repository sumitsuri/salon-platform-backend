package com.salonplatform.dto.packageplan;

import com.salonplatform.domain.enums.PackagePlanType;
import com.salonplatform.domain.enums.PackageRedemptionMode;
import com.salonplatform.domain.enums.PromoStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ServicePackagePlanResponse {
    private UUID id;
    private String name;
    private String description;
    private BigDecimal listPriceTotal;
    private BigDecimal packagePrice;
    private PackagePlanType planType;
    private BigDecimal creditValue;
    private Integer validityDays;
    private PackageRedemptionMode redemptionMode;
    private List<UUID> branchIds;
    private PromoStatus status;
    private Integer predefinedRank;
    private List<ServicePackagePlanItemResponse> items;
    private Instant createdAt;
}
