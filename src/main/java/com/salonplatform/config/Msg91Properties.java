package com.salonplatform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

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

    /**
     * When true, WhatsApp bill receipts are sent only from {@link #billReceiptPilotTenantSlug} /
     * {@link #billReceiptPilotBranchCode}. Other branches skip with a pilot message.
     */
    private boolean billReceiptPilotEnabled = false;

    /** Tenant slug allowed to send WhatsApp bill receipts during pilot (e.g. mystic-wellness). */
    private String billReceiptPilotTenantSlug = "demo-brand";

    /** Branch code allowed to send WhatsApp bill receipts during pilot (e.g. VAR). */
    private String billReceiptPilotBranchCode = "VAR";

    /** Additional tenant slugs allowed for bill receipt WhatsApp during pilot (all branches). */
    private List<String> billReceiptPilotAdditionalTenantSlugs = List.of("mystic-wellness");

    public boolean isEnabled() {
        return authKey != null && !authKey.isBlank();
    }

    public boolean allowsBillReceiptFor(String tenantSlug, String branchCode) {
        if (!billReceiptPilotEnabled) {
            return true;
        }
        if (tenantSlug == null || branchCode == null) {
            return false;
        }
        String slug = tenantSlug.trim();
        if (billReceiptPilotAdditionalTenantSlugs != null) {
            for (String allowed : billReceiptPilotAdditionalTenantSlugs) {
                if (allowed != null && allowed.equalsIgnoreCase(slug)) {
                    return true;
                }
            }
        }
        return billReceiptPilotTenantSlug.equalsIgnoreCase(slug)
                && billReceiptPilotBranchCode.equalsIgnoreCase(branchCode.trim());
    }

    public String billReceiptPilotLabel() {
        return billReceiptPilotTenantSlug + " / " + billReceiptPilotBranchCode;
    }
}
