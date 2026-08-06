package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BenchmarkResponse {
    private String periodLabel;
    private String brandName;
    private String marketCity;
    private String cohortLabel;
    private Integer cohortSize;
    private Integer brandRank;
    private Integer metricsAboveMedian;
    private Integer totalMetrics;
    private BigDecimal estimatedMonthlyOpportunity;
    private List<MetricComparison> heroMetrics;
    private List<MetricComparison> allMetrics;
    private List<BranchRow> branchRankings;
    private List<PeerRow> networkPeers;
    private List<LocalCompetitorRow> localCompetitors;
    private List<PlaybookItem> playbook;
    private boolean benchmarkOptIn;

    @Data
    @Builder
    public static class MetricComparison {
        private String key;
        private String label;
        private BigDecimal yourValue;
        private BigDecimal peerMedian;
        private BigDecimal topQuartile;
        private BigDecimal gapToMedian;
        private BigDecimal gapToTopQuartile;
        private String unit;
        private String direction;
        private String status;
        private Integer percentileRank;
    }

    @Data
    @Builder
    public static class BranchRow {
        private UUID branchId;
        private String branchName;
        private BigDecimal revenuePerBranchDay;
        private BigDecimal avgTicket;
        private BigDecimal visitsPerBranchDay;
        private BigDecimal netMarginPercent;
        private BigDecimal retailAttachPercent;
        private BigDecimal repeatVisitRate;
        private BigDecimal discountLeakagePercent;
        private Integer rankInBrand;
        private Integer branchCount;
        private String brandPercentileLabel;
    }

    @Data
    @Builder
    public static class PeerRow {
        private String peerLabel;
        private String tierLabel;
        private Integer branchCount;
        private BigDecimal revenuePerBranchDay;
        private BigDecimal avgTicket;
        private BigDecimal retailAttachPercent;
        private BigDecimal netMarginPercent;
        private BigDecimal repeatVisitRate;
        private boolean isYou;
    }

    @Data
    @Builder
    public static class LocalCompetitorRow {
        private UUID id;
        private String name;
        private String competitorType;
        private UUID branchId;
        private String branchName;
        private BigDecimal revenuePerBranchDay;
        private BigDecimal avgTicket;
        private BigDecimal retailAttachPercent;
        private BigDecimal netMarginPercent;
        private BigDecimal repeatVisitRate;
        private String address;
        private String notes;
        private Double googleRating;
        private Integer googleReviewCount;
        private Integer gbpPhotoCount;
        private Integer gbpVideoCount;
        private Boolean gbpHasPhone;
        private Integer estimatedSearchRank;
    }

    @Data
    @Builder
    public static class PlaybookItem {
        private String id;
        private String severity;
        private String title;
        private String message;
        private String metricKey;
        private BigDecimal estimatedMonthlyImpact;
        private String actionModule;
        private String actionLabel;
    }
}
