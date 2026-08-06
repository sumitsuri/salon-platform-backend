package com.salonplatform.google;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.google.places")
public class GooglePlacesProperties {

    /** Google Cloud API key with Places API (New) enabled. */
    private String apiKey = "";

    /** Only this branch code receives live Google sync in pilot phase. */
    private String pilotBranchCode = "VAR";

    /** Tenant slug for pilot (demo-brand). */
    private String pilotTenantSlug = "demo-brand";

    /** Re-sync from Google if data is older than this many hours. */
    private int syncCacheHours = 24;

    /** Default nearby search radius in metres. */
    private int defaultRadiusMeters = 2000;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
