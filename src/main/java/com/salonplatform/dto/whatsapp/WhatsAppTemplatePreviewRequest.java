package com.salonplatform.dto.whatsapp;

import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class WhatsAppTemplatePreviewRequest {
    private UUID branchId;
    /** Optional overrides keyed by variable key (e.g. customerName). */
    private Map<String, String> variableOverrides;
}
