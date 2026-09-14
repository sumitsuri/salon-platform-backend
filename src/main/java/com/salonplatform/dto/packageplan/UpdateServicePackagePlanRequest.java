package com.salonplatform.dto.packageplan;

import com.salonplatform.domain.enums.PackageRedemptionMode;
import com.salonplatform.domain.enums.PromoStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class UpdateServicePackagePlanRequest {
    @NotBlank
    private String name;
    private String description;
    @NotNull
    private BigDecimal packagePrice;
    private Integer validityDays = 90;
    private PackageRedemptionMode redemptionMode = PackageRedemptionMode.MULTI_VISIT;
    private List<UUID> branchIds;
    private PromoStatus status = PromoStatus.ACTIVE;
    @NotEmpty
    private List<PackagePlanItemRequest> items;
}
