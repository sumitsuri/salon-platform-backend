package com.salonplatform.dto.promo;

import com.salonplatform.domain.enums.DiscountType;
import com.salonplatform.domain.enums.PromoStatus;
import com.salonplatform.domain.enums.ServiceScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
public class CreateOfferRequest {
    @NotBlank
    private String name;
    private String description;
    @NotNull
    private DiscountType discountType;
    @NotNull
    @Positive
    private BigDecimal discountValue;
    @NotNull
    private Instant startsAt;
    @NotNull
    private Instant endsAt;
    private ServiceScopeType serviceScope = ServiceScopeType.ALL;
    private List<UUID> scopeIds;
    private List<UUID> branchIds;
    private PromoStatus status = PromoStatus.ACTIVE;
    private Integer maxRedemptionsTotal;
}
