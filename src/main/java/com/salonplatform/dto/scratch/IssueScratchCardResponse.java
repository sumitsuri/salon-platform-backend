package com.salonplatform.dto.scratch;

import com.salonplatform.domain.enums.ScratchCardStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class IssueScratchCardResponse {
    private UUID cardId;
    private String publicToken;
    private String scratchUrl;
    private String campaignName;
    private ScratchCardStatus status;
}
