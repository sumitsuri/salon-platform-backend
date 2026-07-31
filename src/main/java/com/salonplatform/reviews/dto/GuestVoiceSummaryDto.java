package com.salonplatform.reviews.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class GuestVoiceSummaryDto {
    private double averageRating;
    private long totalReviews;
    private long promotersCount;
    private long detractorsCount;
    private Map<Integer, Long> ratingDistribution;
    private Map<String, Long> improvementTagCounts;
    private List<RecoveryItemDto> openRecoveries;

    @Data
    @Builder
    public static class RecoveryItemDto {
        private UUID recoveryId;
        private UUID visitId;
        private UUID branchId;
        private int overallRating;
        private String status;
        private Instant createdAt;
    }
}
