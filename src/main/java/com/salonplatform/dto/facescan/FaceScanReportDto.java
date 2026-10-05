package com.salonplatform.dto.facescan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaceScanReportDto {
    private FaceScanMetricsDto metrics;
    private List<FaceScanConcernDto> concerns;
    private List<FaceScanRoutineStepDto> routineSteps;
    private List<FaceScanServiceSuggestionDto> inSalonServices;
    private String disclaimer;
}
