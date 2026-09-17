package com.salonplatform.dto.scratch;

import com.salonplatform.domain.enums.PromoStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ScratchCampaignResponse {
    private UUID id;
    private String name;
    private String description;
    private PromoStatus status;
    private Instant startsAt;
    private Instant endsAt;
    private Integer cardsValidDays;
    private List<ScratchCampaignPrizeResponse> prizes;
    private Instant createdAt;
    private Integer cardsIssued;
    private Integer cardsRedeemed;
}
