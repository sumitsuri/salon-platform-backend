package com.salonplatform.dto.facescan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaceScanCarePlanSummaryDto {
    private int durationMonths;
    private String headline;
    private String approachNote;
}
