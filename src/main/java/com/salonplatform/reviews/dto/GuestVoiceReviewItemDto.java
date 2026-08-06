package com.salonplatform.reviews.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class GuestVoiceReviewItemDto {
    private UUID reviewId;
    private UUID visitId;
    private UUID branchId;
    private String branchName;
    private String customerFirstName;
    private int overallRating;
    private Map<String, Integer> categoryRatings;
    private List<String> improvementTags;
    private String comment;
    private Instant submittedAt;
    private boolean googleReviewRedirected;
}
