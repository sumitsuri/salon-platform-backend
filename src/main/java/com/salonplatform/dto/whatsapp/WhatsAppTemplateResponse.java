package com.salonplatform.dto.whatsapp;

import com.salonplatform.domain.enums.WhatsAppTemplateCategory;
import com.salonplatform.domain.enums.WhatsAppTemplateCode;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class WhatsAppTemplateResponse {
    private WhatsAppTemplateCode code;
    private String displayName;
    private String msg91TemplateName;
    private WhatsAppTemplateCategory category;
    private String triggerDescription;
    private String displayBody;
    private boolean wired;
    private boolean active;
    private UUID branchId;
    private String branchName;
    private boolean hasDocumentHeader;
    private List<WhatsAppTemplateVariableResponse> variables;
}
