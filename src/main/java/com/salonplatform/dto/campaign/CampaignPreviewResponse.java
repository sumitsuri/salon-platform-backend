package com.salonplatform.dto.campaign;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CampaignPreviewResponse {
    private long matchingCustomers;
}
