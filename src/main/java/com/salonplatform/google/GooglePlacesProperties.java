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

    /** Minimum delay between consecutive Places API HTTP calls (reduces 429 during sync). */
    private int minIntervalBetweenRequestsMs = 350;

    /** Max retries after HTTP 429 before failing the call. */
    private int rateLimitMaxRetries = 3;

    /** Base backoff (ms) for 429 retries; doubled each attempt up to 30s. */
    private int rateLimitBackoffMs = 2_000;

    /**
     * When true, a force refresh reuses today's cached keyword SERPs when available
     * (listing/rivals still refresh). Reduces quota use on repeated Refresh clicks.
     */
    private boolean reuseKeywordSerpCacheOnForce = false;

    /** When true, Places search calls go through production (for IP-restricted prod keys). */
    private boolean internalProxyEnabled = false;

    /** e.g. https://api.antrahq.com */
    private String internalProxyBaseUrl = "";

    /** Shared secret for {@code /api/v1/internal/places/*} (server validates; local client sends). */
    private String internalProxySecret = "";

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean useInternalProxy() {
        return internalProxyEnabled && isInternalProxyConfigured();
    }

    public boolean isInternalProxyConfigured() {
        return internalProxyBaseUrl != null
                && !internalProxyBaseUrl.isBlank()
                && internalProxySecret != null
                && !internalProxySecret.isBlank();
    }
}
