package com.salonplatform.whatsapp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WhatsAppTemplateVariable {
    /** Stable key used in preview API (e.g. customerName). */
    private String key;
    /** Human label shown in admin UI. */
    private String label;
    /** Meta positional variable index (1-based). */
    private int metaIndex;
    /** Sample value for preview and Meta submission. */
    private String sampleValue;
}
