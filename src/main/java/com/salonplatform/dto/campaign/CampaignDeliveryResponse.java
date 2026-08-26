package com.salonplatform.dto.campaign;

import com.salonplatform.domain.enums.MessageDeliveryStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CampaignDeliveryResponse {
    private UUID id;
    private UUID customerId;
    private String customerName;
    private String recipientPhone;
    private MessageDeliveryStatus status;
    private String errorMessage;
    private String providerMessageId;
    private Instant createdAt;
}
