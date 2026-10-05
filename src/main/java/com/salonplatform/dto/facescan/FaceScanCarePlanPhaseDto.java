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
public class FaceScanCarePlanPhaseDto {
    /** 1-based month in the recommended program (typically 1–3). */
    private int month;
    private String title;
    private String goal;
    /** Why this stage comes now, tied to scan signals. */
    private String rationale;
    private String visitCadence;
    private FaceScanServiceSuggestionDto inSalonVisit;
    private List<FaceScanRoutineStepDto> homeRoutine;
}
