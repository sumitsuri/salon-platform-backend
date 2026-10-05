package com.salonplatform.dto.facescan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaceScanMetricsDto {
    private int skinHealthScore;
    private int oilinessIndex;
    private int hydrationIndex;
    private int textureIndex;
    private int rednessIndex;
}
