package com.salonplatform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.msg91")
public class Msg91Properties {

    /** MSG91 auth key. When blank, outbound messaging is disabled (dev-friendly). */
    private String authKey = "";

    /** WhatsApp integrated number (e.g. 919876543210). */
    private String whatsappIntegratedNumber = "";

    /** Approved WhatsApp template name for bill receipts (utility). */
    private String billReceiptTemplate = "antrahq_bill_receipt_v1";

    /** Approved WhatsApp template name for marketing promos. */
    private String promoTemplate = "antrahq_salon_promo_v1";

    /** Approved WhatsApp template for online appointment confirmation. */
    private String appointmentConfirmedTemplate = "antrahq_salon_appointment_confirmed_v2";

    /** SMS Flow API flow id for marketing messages. */
    private String promoSmsFlowId = "";

    /** DLT-registered SMS sender id. */
    private String smsSender = "";

    public boolean isEnabled() {
        return authKey != null && !authKey.isBlank();
    }
}
