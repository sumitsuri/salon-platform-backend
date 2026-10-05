package com.salonplatform.service.scalpscan;

public record ScalpScanImageMetrics(
        double meanBrightness,
        double rednessIndex,
        double textureVariance,
        double uvFluorescenceScore,
        double edgeDensity
) {}
