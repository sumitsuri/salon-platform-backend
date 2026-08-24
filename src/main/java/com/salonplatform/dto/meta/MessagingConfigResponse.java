package com.salonplatform.dto.meta;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MessagingConfigResponse {
    private boolean msg91Enabled;
    private String whatsappNumber;
    private String billReceiptTemplate;
    private String promoTemplate;
    private String appointmentConfirmedTemplate;
    private String apiPublicUrl;
}
