package com.salonplatform.dto.membership;

import com.salonplatform.domain.enums.MembershipCadence;
import com.salonplatform.domain.enums.PromoStatus;
import com.salonplatform.domain.enums.ServiceScopeType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class MembershipPlanResponse {
    private UUID id;
    private String name;
    private String description;
    private MembershipCadence cadence;
    private BigDecimal feeAmount;
    private BigDecimal benefitPercent;
    private ServiceScopeType serviceScope;
    private List<UUID> scopeIds;
    private List<UUID> branchIds;
    private PromoStatus status;
    private Instant createdAt;
}
