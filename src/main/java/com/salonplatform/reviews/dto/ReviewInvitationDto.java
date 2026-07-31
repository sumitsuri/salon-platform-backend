package com.salonplatform.reviews.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ReviewInvitationDto {
    private UUID invitationId;
    private UUID visitId;
    private String token;
    private String reviewUrl;
    private String status;
    private Instant expiresAt;
    private Integer submittedRating;
}
