package com.salonplatform.dto.platform;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Account-level adoption analytics for the platform "Customer usage" area: period-over-period change, an
 * explainable health score, per-feature last-used times and trends, and feature × branch / user matrices.
 */
public final class PlatformUsageInsights {

    private PlatformUsageInsights() {}

    /** Why an account scores what it does — every component is 0..100 so the UI can show the breakdown. */
    @Data
    @Builder
    public static class Health {
        /** Composite 0..100. */
        private int score;
        /** HEALTHY, WATCH, AT_RISK, DORMANT or NEW. */
        private String status;
        /** Share of provisioned users who were active in the range (breadth of people). */
        private int adoption;
        /** Distinct features used vs a core set of up to 8 (depth). */
        private int breadth;
        /** WAU / MAU at the end of the range. */
        private int stickiness;
        /** Events vs the previous period of equal length (50 = flat). */
        private int momentum;
    }

    @Data
    @Builder
    public static class Totals {
        private int events;
        private int previousEvents;
        private int activeUsers;
        private int previousActiveUsers;
        private int wau;
        private int mau;
        private int provisionedUsers;
        private int distinctFeatures;
        private Instant lastActivityAt;
    }

    @Data
    @Builder
    public static class SegmentCount {
        private String status;
        private int count;
    }

    @Data
    @Builder
    public static class AccountInsight {
        private UUID tenantId;
        private String tenantName;
        private String tenantSlug;
        private boolean demoTenant;
        private Totals totals;
        private Health health;
        /** Daily event counts across the range, zero-filled, oldest first. */
        private List<Integer> trend;
    }

    @Data
    @Builder
    public static class FeatureReach {
        private String featureKey;
        private int hits;
        private int previousHits;
        private int uniqueUsers;
        /** Brands that used this feature in the range. */
        private int brands;
        private Instant lastUsedAt;
        private List<Integer> trend;
    }

    @Data
    @Builder
    public static class Portfolio {
        private Instant from;
        private Instant to;
        private LocalDate previousFrom;
        private LocalDate previousTo;
        private int rangeDays;
        private boolean telemetryReady;
        private Totals platform;
        private List<SegmentCount> segments;
        private List<AccountInsight> accounts;
        private List<FeatureReach> featureReach;
        /** Distinct features seen platform-wide in the range (denominator for brand reach). */
        private int platformFeatureCount;
        private int totalBrands;
    }

    @Data
    @Builder
    public static class DailyPoint {
        private LocalDate date;
        private int events;
        private int activeUsers;
        private int admin;
        private int manager;
        private int employee;
        private int other;
    }

    @Data
    @Builder
    public static class FeatureRow {
        private String featureKey;
        private int hits;
        private int previousHits;
        private int uniqueUsers;
        /** uniqueUsers as a share of the brand's provisioned users, 0..100. */
        private int userReachPct;
        private Instant firstUsedAt;
        private Instant lastUsedAt;
        private List<Integer> trend;
    }

    @Data
    @Builder
    public static class BranchRow {
        private UUID branchId;
        private String branchName;
        private String branchCode;
        private int provisionedUsers;
        private int activeUsers;
        private int events;
        private int previousEvents;
        private int distinctFeatures;
        private String topFeatureKey;
        private Instant lastActivityAt;
        private List<Integer> trend;
    }

    @Data
    @Builder
    public static class UserRow {
        private UUID userId;
        private String name;
        private String email;
        private String role;
        private UUID branchId;
        private String branchName;
        private boolean active;
        private int events;
        private int previousEvents;
        private int featuresUsed;
        private int activeDays;
        private String topFeatureKey;
        private Instant lastSeenAt;
        /** Only populated for the most active users (empty otherwise). */
        private List<Integer> trend;
    }

    /** One cell of a feature × branch or feature × user grid. */
    @Data
    @Builder
    public static class MatrixCell {
        private String featureKey;
        /** Branch id or user id, depending on the matrix. */
        private UUID columnId;
        private int hits;
        private Instant lastUsedAt;
    }

    @Data
    @Builder
    public static class ActivityEvent {
        private Instant occurredAt;
        private UUID userId;
        private String userName;
        private String role;
        private String branchName;
        private String portal;
        private String featureKey;
    }

    @Data
    @Builder
    public static class Tenant {
        private UUID tenantId;
        private String tenantName;
        private Instant from;
        private Instant to;
        private LocalDate previousFrom;
        private LocalDate previousTo;
        private int rangeDays;
        private boolean telemetryReady;
        private Totals totals;
        private Health health;
        private List<DailyPoint> daily;
        private List<FeatureRow> features;
        private List<BranchRow> branches;
        private List<UserRow> users;
        private List<MatrixCell> featureBranchMatrix;
        private List<MatrixCell> featureUserMatrix;
        private List<ActivityEvent> recentActivity;
    }
}
