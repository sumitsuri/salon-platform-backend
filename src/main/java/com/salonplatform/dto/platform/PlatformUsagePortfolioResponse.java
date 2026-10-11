package com.salonplatform.dto.platform;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PlatformUsagePortfolioResponse {
    private Instant from;
    private Instant to;
    private PlatformUsageAnalyticsResponse.UsageSummary platformSummary;
    private List<DailyPoint> dailyActiveUsers;
    private List<PlatformUsageSummaryResponse.FeatureHit> topFeatures;
    private List<TenantRow> tenants;

    @Data
    @Builder
    public static class DailyPoint {
        private LocalDate date;
        private int activeUsers;
        private int eventCount;
    }

    @Data
    @Builder
    public static class TenantRow {
        private UUID tenantId;
        private String tenantName;
        private String tenantSlug;
        private boolean demoTenant;
        private int provisionedUsers;
        private int wau;
        private int mau;
        private int activeUsersInRange;
        private int totalEvents;
        private Instant lastActivityAt;
        private List<PlatformUsageSummaryResponse.FeatureHit> topFeatures;
    }
}
