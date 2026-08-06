package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class LocalSpotlightResponse {
    private int localVisibilityScore;
    private String scoreLabel;
    private int branchesLinked;
    private int branchesTotal;
    private int notInTop3Count;
    private int ratingBelowRivalsCount;
    private int incompleteGbpCount;
    private int missingPhoneCount;
    private String dataSourceNote;
    private Instant lastRefreshedAt;
    private boolean googleApiConfigured;
    private boolean pilotMode;
    private String pilotBranchCode;
    private String pilotBranchName;
    private String syncStatusMessage;
    private List<BranchRow> branches;
    private List<RivalRow> rivals;
    private List<SearchRankRow> searchRanks;
    private List<PlaybookItem> playbook;

    @Data
    @Builder
    public static class BranchRow {
        private UUID branchId;
        private String branchName;
        private String branchCode;
        private String localityLabel;
        private String businessType;
        private int localVisibilityScore;
        private String scoreLabel;
        private Integer estimatedSearchRank;
        private boolean inTop3;
        private Double googleRating;
        private Integer googleReviewCount;
        private Integer googleLowRatingReviewCount;
        private Integer googleReviewsSampleSize;
        private int gbpCompletenessPercent;
        private boolean listingLinked;
        private boolean googleSynced;
        private boolean pilotBranch;
        private boolean gbpHasPhone;
        private boolean gbpHasWebsite;
        private boolean gbpHasHours;
        private boolean gbpHasBookButton;
        private Integer gbpPhotoCount;
        private Integer gbpVideoCount;
        private Integer gbpServicesListedCount;
        private String googlePlaceId;
        private String googleMapsUrl;
        private String googleReviewUrl;
        private Boolean googleReviewAutoPublish;
        private String googleFormattedAddress;
        private Double latitude;
        private Double longitude;
        private Instant digitalPresenceUpdatedAt;
        private int trackedRivalCount;
    }

    @Data
    @Builder
    public static class RivalRow {
        private UUID id;
        private String name;
        private UUID branchId;
        private String branchName;
        private Double googleRating;
        private Integer googleReviewCount;
        private Integer googleLowRatingReviewCount;
        private Integer googleReviewsSampleSize;
        private Integer gbpPhotoCount;
        private Integer gbpVideoCount;
        private Boolean gbpHasPhone;
        private Integer estimatedSearchRank;
        private String address;
        private String googlePlaceId;
        private String googleMapsUrl;
        private boolean googleAutoDiscovered;
    }

    @Data
    @Builder
    public static class SearchRankRow {
        private UUID branchId;
        private String branchName;
        private String keyword;
        private Integer yourRank;
        private boolean yourRankBeyondTop20;
        private String yourRankLabel;
        private boolean inTop3;
        private String topThreeSummary;
        private List<TopThreeRival> topThreeRivals;
    }

    @Data
    @Builder
    public static class TopThreeRival {
        private int rank;
        private String name;
        private String googleMapsUrl;
        private String googlePlaceId;
    }

    @Data
    @Builder
    public static class PlaybookItem {
        private String id;
        private String severity;
        /** UI grouping: GOAL, KEYWORDS, PROFILE, REVIEWS, CONTENT, REPUTATION */
        private String section;
        /** Human-readable sub-group within the section (e.g. Map pack entry). */
        private String subCategory;
        private String title;
        private String message;
        /** Data-backed explanation from Local Spotlight metrics vs rivals. */
        private String reasoning;
        /** Single keyword legacy field; prefer keywords for grouped actions. */
        private String keyword;
        /** Keywords this action applies to when grouped. */
        private List<String> keywords;
        private String metricKey;
        /**
         * Navigation target: tab:search | tab:branches | tab:rivals | route:/admin/... |
         * external:googleMaps | external:googleReviews | action:syncGoogle
         */
        private String actionTarget;
        private String actionModule;
        private String actionLabel;
        private UUID branchId;
        private String branchName;
    }
}
