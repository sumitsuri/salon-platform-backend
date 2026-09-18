package com.salonplatform.dto.packageplan;

import com.salonplatform.domain.enums.PackagePlanType;
import com.salonplatform.domain.enums.PackageRedemptionMode;
import com.salonplatform.domain.enums.PromoStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class CreateServicePackagePlanRequest {
    @NotBlank
    private String name;
    private String description;
    @NotNull
    private BigDecimal packagePrice;
    private Integer validityDays = 90;
    private PackageRedemptionMode redemptionMode = PackageRedemptionMode.MULTI_VISIT;
    private List<UUID> branchIds;
    private PromoStatus status = PromoStatus.ACTIVE;
    private PackagePlanType planType = PackagePlanType.SERVICE_BUNDLE;
    /** Required when planType is VALUE_CREDIT. */
    private BigDecimal creditValue;
    private List<PackagePlanItemRequest> items;
}
