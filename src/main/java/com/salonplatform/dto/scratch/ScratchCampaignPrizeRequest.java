package com.salonplatform.dto.scratch;

import com.salonplatform.domain.enums.DiscountType;
import com.salonplatform.domain.enums.ScratchPrizeKind;
import com.salonplatform.domain.enums.ServiceScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class ScratchCampaignPrizeRequest {
    @NotBlank
    private String label;
    @NotNull
    private ScratchPrizeKind prizeKind;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private ServiceScopeType serviceScope;
    private List<UUID> scopeIds;
    @Positive
    private Integer weight = 1;
    private Integer sortOrder;
}
