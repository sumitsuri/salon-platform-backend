package com.salonplatform.dto.promo;

import com.salonplatform.domain.enums.DiscountType;
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
public class CouponResponse {
    private UUID id;
    private String name;
    private String code;
    private String description;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private Instant startsAt;
    private Instant endsAt;
    private ServiceScopeType serviceScope;
    private List<UUID> scopeIds;
    private List<UUID> branchIds;
    private PromoStatus status;
    private Integer maxRedemptionsTotal;
    private Integer redemptionCount;
    private Instant createdAt;
}
