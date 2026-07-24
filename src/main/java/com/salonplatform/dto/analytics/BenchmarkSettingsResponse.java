package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BenchmarkSettingsResponse {
    private boolean benchmarkOptIn;
    private String marketCity;
    private String salonTier;
}
