package com.salonplatform.dto.scratch;

import com.salonplatform.domain.enums.DiscountType;
import com.salonplatform.domain.enums.ScratchPrizeKind;
import com.salonplatform.domain.enums.ServiceScopeType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ScratchCampaignPrizeResponse {
    private UUID id;
    private String label;
    private ScratchPrizeKind prizeKind;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private ServiceScopeType serviceScope;
    private List<UUID> scopeIds;
    private Integer weight;
    private Integer sortOrder;
}
