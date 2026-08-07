package com.salonplatform.reviews.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubmitPublicReviewResponse {
    private int overallRating;
    private boolean promptGoogleReview;
    private String googleReviewUrl;
    /** Client may open googleReviewUrl in a new tab without surfacing Google in primary UX. */
    private boolean autoRedirectGoogle;
    /** Server marked the review as routed to Google (4★+ with auto-publish enabled). */
    private boolean googleReviewAutoPublished;
    /** Draft text the guest can paste on Google (comment, or a short generated summary). */
    private String suggestedPublicReviewText;
    private boolean recoveryCreated;
    private String thankYouMessage;
}
