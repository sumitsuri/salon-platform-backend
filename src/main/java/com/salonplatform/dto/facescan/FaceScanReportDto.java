package com.salonplatform.dto.facescan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.salonplatform.dto.scan.ScanAnalysisMetaDto;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaceScanReportDto {
    private FaceScanMetricsDto metrics;
    private List<FaceScanConcernDto> concerns;
    /** Legacy flat list; prefer {@link #carePlanPhases}. */
    private List<FaceScanRoutineStepDto> routineSteps;
    /** Legacy flat list; prefer staged {@link #carePlanPhases}. */
    private List<FaceScanServiceSuggestionDto> inSalonServices;
    private FaceScanCarePlanSummaryDto carePlanSummary;
    private List<FaceScanCarePlanPhaseDto> carePlanPhases;
    private ScanAnalysisMetaDto analysisMeta;
    private String disclaimer;
}
