package com.salonplatform.dto.membership;

import com.salonplatform.domain.enums.MembershipCadence;
import com.salonplatform.domain.enums.PromoStatus;
import com.salonplatform.domain.enums.ServiceScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class CreateMembershipPlanRequest {
    @NotBlank
    private String name;
    private String description;
    @NotNull
    private MembershipCadence cadence;
    @NotNull
    @Positive
    private BigDecimal feeAmount;
    @Positive
    private BigDecimal benefitPercent;
    private ServiceScopeType serviceScope = ServiceScopeType.ALL;
    private List<UUID> scopeIds;
    private List<UUID> branchIds;
    private PromoStatus status = PromoStatus.ACTIVE;
}
