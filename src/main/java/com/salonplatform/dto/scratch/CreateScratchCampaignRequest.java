package com.salonplatform.dto.scratch;

import com.salonplatform.domain.enums.PromoStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class CreateScratchCampaignRequest {
    @NotBlank
    private String name;
    private String description;
    @NotNull
    private Instant startsAt;
    @NotNull
    private Instant endsAt;
    private Integer cardsValidDays = 14;
    private PromoStatus status = PromoStatus.ACTIVE;
    @NotEmpty
    @Valid
    private List<ScratchCampaignPrizeRequest> prizes;
}
