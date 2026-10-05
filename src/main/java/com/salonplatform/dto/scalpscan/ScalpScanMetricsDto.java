package com.salonplatform.dto.scalpscan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScalpScanMetricsDto {
    private int scalpHealthScore;
    private int oilinessIndex;
    private int hydrationIndex;
    private int densityIndex;
    private int irritationIndex;
}
