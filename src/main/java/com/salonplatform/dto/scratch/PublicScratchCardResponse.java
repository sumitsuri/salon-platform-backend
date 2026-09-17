package com.salonplatform.dto.scratch;

import com.salonplatform.domain.enums.ScratchCardStatus;
import com.salonplatform.domain.enums.ScratchPrizeKind;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class PublicScratchCardResponse {
    private String tenantName;
    private String tenantLogoUrl;
    private String primaryColor;
    private String branchName;
    private String campaignName;
    private String campaignDescription;
    private ScratchCardStatus status;
    private boolean scratched;
    private boolean contactRequired;
    private boolean redeemable;
    private String prizeLabel;
    private ScratchPrizeKind prizeKind;
    private String prizeHeadline;
    private String redemptionCode;
    private Instant expiresAt;
}
