package com.salonplatform.dto.scalpscan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScalpScanReportDto {
    private ScalpScanMetricsDto metrics;
    private List<ScalpScanConcernDto> concerns;
    private List<ScalpScanRoutineStepDto> routineSteps;
    private List<ScalpScanServiceSuggestionDto> inSalonServices;
    private String disclaimer;
}
