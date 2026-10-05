package com.salonplatform.service.facescan;

public record FaceScanImageMetrics(
        double meanBrightness,
        double rednessIndex,
        double textureVariance,
        double uvFluorescenceScore
) {}
