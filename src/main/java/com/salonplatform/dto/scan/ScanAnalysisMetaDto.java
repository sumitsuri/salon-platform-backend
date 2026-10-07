package com.salonplatform.dto.scan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanAnalysisMetaDto {
    /** HIGH, MEDIUM, LOW — how much to trust automated signals. */
    private String confidence;
    /** HEURISTIC or LLM_ASSISTED */
    private String method;
    private List<String> qualityIssues;
    private String retakeHint;
    private boolean staffInputUsed;
}
