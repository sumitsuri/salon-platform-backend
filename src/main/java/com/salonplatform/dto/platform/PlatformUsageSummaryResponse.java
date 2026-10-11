package com.salonplatform.dto.platform;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PlatformUsageSummaryResponse {
    private Instant from;
    private Instant to;
    private List<TenantUsageRow> tenants;

    @Data
    @Builder
    public static class TenantUsageRow {
        private UUID tenantId;
        private String tenantName;
        private String tenantSlug;
        private boolean demoTenant;
        private int provisionedUsers;
        private int wau;
        private int mau;
        private Instant lastActivityAt;
        private List<FeatureHit> topFeatures;
    }

    @Data
    @Builder
    public static class FeatureHit {
        private String featureKey;
        private int hitCount;
    }
}
