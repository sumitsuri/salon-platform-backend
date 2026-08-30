package com.salonplatform.whatsapp;

import com.salonplatform.domain.enums.WhatsAppTemplateCategory;
import com.salonplatform.domain.enums.WhatsAppTemplateCode;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WhatsAppTemplateDefinition {
    private WhatsAppTemplateCode code;
    private String displayName;
    /** Approved template name in MSG91 / Meta (shared Antrahq WABA). */
    private String msg91TemplateName;
    private WhatsAppTemplateCategory category;
    /** When this message is sent in the product. */
    private String triggerDescription;
    /** Meta-approved body with {{1}}, {{2}}, … placeholders. */
    private String bodyTemplate;
    /** Zenoti-style preview text with {FirstName} tokens for the admin table. */
    private String displayBody;
    private List<WhatsAppTemplateVariable> variables;
    /** Whether backend send logic exists today. */
    private boolean wired;
    /** Default on/off for new tenants. */
    private boolean defaultActive;
    /** DOCUMENT header (e.g. bill PDF) vs plain body. */
    private boolean hasDocumentHeader;
}
