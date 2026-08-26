package com.salonplatform.dto.campaign;

import com.salonplatform.domain.enums.CampaignStatus;
import com.salonplatform.domain.enums.MessageChannel;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CampaignListFilter {
    private String name;
    private MessageChannel channel;
    private CampaignStatus status;
}
