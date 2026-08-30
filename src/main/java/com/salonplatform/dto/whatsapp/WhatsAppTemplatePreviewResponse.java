package com.salonplatform.dto.whatsapp;

import com.salonplatform.domain.enums.WhatsAppTemplateCategory;
import com.salonplatform.domain.enums.WhatsAppTemplateCode;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WhatsAppTemplatePreviewResponse {
    private WhatsAppTemplateCode code;
    private String displayName;
    private String msg91TemplateName;
    private WhatsAppTemplateCategory category;
    private boolean hasDocumentHeader;
    private String headerPreview;
    private String bodyText;
    private List<WhatsAppTemplateVariableResponse> variables;
    private String metaNote;
}
