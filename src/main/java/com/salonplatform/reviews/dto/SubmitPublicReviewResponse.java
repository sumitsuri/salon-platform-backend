package com.salonplatform.reviews.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubmitPublicReviewResponse {
    private int overallRating;
    private boolean promptGoogleReview;
    private String googleReviewUrl;
    /** Client should immediately open googleReviewUrl in a new tab. */
    private boolean autoRedirectGoogle;
    /** Server marked the review as routed to Google (4★+ with auto-publish enabled). */
    private boolean googleReviewAutoPublished;
    private boolean recoveryCreated;
    private String thankYouMessage;
}
