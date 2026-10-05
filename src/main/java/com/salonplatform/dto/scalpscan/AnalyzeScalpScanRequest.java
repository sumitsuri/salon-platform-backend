package com.salonplatform.dto.scalpscan;

import lombok.Data;

@Data
public class AnalyzeScalpScanRequest {
    /** Comma-separated concern codes the stylist confirms (optional). */
    private String staffConfirmedConcerns;
    private String staffNotes;
}
