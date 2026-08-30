package com.salonplatform.dto.whatsapp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WhatsAppTemplateVariableResponse {
    private String key;
    private String label;
    private int metaIndex;
    private String sampleValue;
}
