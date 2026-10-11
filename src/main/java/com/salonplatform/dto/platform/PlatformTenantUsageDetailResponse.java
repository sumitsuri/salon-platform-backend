package com.salonplatform.dto.platform;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PlatformTenantUsageDetailResponse {
    private UUID tenantId;
    private String tenantName;
    private Instant from;
    private Instant to;
    private int provisionedUsers;
    private int wau;
    private int mau;
    private List<DailyActiveUsers> dailyActiveUsers;
    private List<PlatformUsageSummaryResponse.FeatureHit> topFeatures;
    private List<UserUsageRow> users;

    @Data
    @Builder
    public static class DailyActiveUsers {
        private LocalDate date;
        private int activeUsers;
    }

    @Data
    @Builder
    public static class UserUsageRow {
        private UUID userId;
        private String name;
        private String email;
        private String role;
        private UUID branchId;
        private boolean active;
        private Instant lastSeenAt;
        private String lastPortal;
        private String lastFeatureKey;
        private int loginCount;
        private int daysActive28d;
        private boolean everLoggedIn;
    }
}
