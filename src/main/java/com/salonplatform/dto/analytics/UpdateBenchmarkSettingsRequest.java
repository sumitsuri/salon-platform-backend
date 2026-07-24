package com.salonplatform.dto.analytics;

import lombok.Data;

@Data
public class UpdateBenchmarkSettingsRequest {
    private Boolean benchmarkOptIn;
    private String marketCity;
    private String salonTier;
}
