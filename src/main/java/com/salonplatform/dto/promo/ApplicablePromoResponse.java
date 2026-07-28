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
public class ApplicablePromoResponse {
    private UUID id;
    private String kind; // COUPON or OFFER
    private String name;
    private String code;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private ServiceScopeType serviceScope;
    private List<UUID> scopeIds;
    private Instant endsAt;
    private PromoStatus status;
}
