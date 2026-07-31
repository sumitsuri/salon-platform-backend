package com.salonplatform.reviews.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubmitPublicReviewResponse {
    private int overallRating;
    private boolean promptGoogleReview;
    private String googleReviewUrl;
    private boolean recoveryCreated;
    private String thankYouMessage;
}
