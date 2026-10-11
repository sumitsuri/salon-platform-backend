package com.salonplatform.dto.platform;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PlatformUsageAnalyticsResponse {
    private Instant from;
    private Instant to;
    private UUID tenantId;
    private String tenantName;
    private UsageSummary summary;
    private List<DailyPoint> dailyActiveUsers;
    private List<FeatureUsageRow> featureUsage;
    private List<BranchUsageRow> branchUsage;
    private List<UserUsageRow> users;

    @Data
    @Builder
    public static class UsageSummary {
        private int provisionedUsers;
        private int wau;
        private int mau;
        private int activeUsersInRange;
        private int totalEvents;
        private int distinctFeatures;
    }

    @Data
    @Builder
    public static class DailyPoint {
        private LocalDate date;
        private int activeUsers;
        private int eventCount;
    }

    @Data
    @Builder
    public static class FeatureUsageRow {
        private String featureKey;
        private int hitCount;
        private int uniqueUsers;
    }

    @Data
    @Builder
    public static class BranchUsageRow {
        private UUID branchId;
        private String branchName;
        private String branchCode;
        private int activeUsers;
        private int eventCount;
        private String topFeatureKey;
    }

    @Data
    @Builder
    public static class UserUsageRow {
        private UUID userId;
        private String name;
        private String email;
        private String role;
        private UUID branchId;
        private String branchName;
        private boolean active;
        private Instant lastSeenAt;
        private String lastPortal;
        private String lastFeatureKey;
        private int loginCount;
        private int daysActive28d;
        private int eventsInRange;
        private int featuresUsedInRange;
        private boolean everLoggedIn;
    }
}
