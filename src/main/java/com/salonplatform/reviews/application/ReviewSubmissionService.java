package com.salonplatform.reviews.application;

import com.salonplatform.exception.BadRequestException;
import com.salonplatform.reviews.domain.entity.Review;
import com.salonplatform.reviews.domain.entity.ReviewInvitation;
import com.salonplatform.reviews.domain.entity.ReviewRecovery;
import com.salonplatform.reviews.domain.enums.ImprovementTag;
import com.salonplatform.reviews.domain.enums.ReviewCategory;
import com.salonplatform.reviews.domain.enums.RecoveryStatus;
import com.salonplatform.reviews.domain.enums.ReviewInvitationStatus;
import com.salonplatform.reviews.domain.repository.ReviewInvitationRepository;
import com.salonplatform.reviews.domain.repository.ReviewRecoveryRepository;
import com.salonplatform.reviews.domain.repository.ReviewRepository;
import com.salonplatform.reviews.dto.PublicReviewContextDto;
import com.salonplatform.reviews.dto.ReviewCategoryOptionDto;
import com.salonplatform.reviews.dto.SubmitPublicReviewRequest;
import com.salonplatform.reviews.dto.SubmitPublicReviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewSubmissionService {

    private final ReviewTokenService tokenService;
    private final ReviewInvitationRepository invitationRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewRecoveryRepository recoveryRepository;

    @Transactional(readOnly = true)
    public PublicReviewContextDto getContext(String token) {
        UUID invitationId = tokenService.validateAndGetInvitationId(token);
        ReviewInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new BadRequestException("Review invitation not found"));

        expireIfNeeded(invitation);
        Review existing = reviewRepository.findByInvitationId(invitationId).orElse(null);

        return PublicReviewContextDto.builder()
                .branchName(invitation.getBranchName())
                .customerFirstName(invitation.getCustomerFirstName())
                .status(invitation.getStatus().name())
                .alreadySubmitted(existing != null)
                .submittedRating(existing != null ? existing.getOverallRating() : null)
                .googleReviewUrl(invitation.getGoogleReviewUrl())
                .improvementTagOptions(Arrays.stream(ImprovementTag.values()).map(Enum::name).toList())
                .categoryOptions(categoryOptions())
                .build();
    }

    @Transactional
    public SubmitPublicReviewResponse submit(SubmitPublicReviewRequest request) {
        UUID invitationId = tokenService.validateAndGetInvitationId(request.getToken());
        ReviewInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new BadRequestException("Review invitation not found"));

        expireIfNeeded(invitation);
        if (invitation.getStatus() == ReviewInvitationStatus.EXPIRED) {
            throw new BadRequestException("Review link expired");
        }
        if (reviewRepository.findByInvitationId(invitationId).isPresent()) {
            throw new BadRequestException("Review already submitted");
        }

        int rating = request.getOverallRating();
        Map<String, Integer> categoryRatings = validateCategoryRatings(request.getCategoryRatings());
        List<ImprovementTag> tags = parseTags(request.getImprovementTags());
        String comment = sanitizeComment(request.getComment());

        Review review = reviewRepository.save(Review.builder()
                .invitationId(invitationId)
                .tenantId(invitation.getTenantId())
                .branchId(invitation.getBranchId())
                .visitId(invitation.getVisitId())
                .overallRating(rating)
                .serviceRating(categoryRatings.get(ReviewCategory.SERVICE.name()))
                .ambienceRating(categoryRatings.get(ReviewCategory.AMBIENCE.name()))
                .staffRating(categoryRatings.get(ReviewCategory.STAFF.name()))
                .cleanlinessRating(categoryRatings.get(ReviewCategory.CLEANLINESS.name()))
                .valueRating(categoryRatings.get(ReviewCategory.VALUE_FOR_MONEY.name()))
                .improvementTags(serializeTags(tags))
                .comment(comment)
                .googleReviewRedirected(request.isGoogleReviewRedirected())
                .build());

        invitation.setStatus(ReviewInvitationStatus.SUBMITTED);
        invitation.setSubmittedAt(Instant.now());
        invitationRepository.save(invitation);

        boolean recoveryCreated = false;
        if (rating <= 3) {
            recoveryRepository.save(ReviewRecovery.builder()
                    .reviewId(review.getId())
                    .tenantId(invitation.getTenantId())
                    .branchId(invitation.getBranchId())
                    .visitId(invitation.getVisitId())
                    .overallRating(rating)
                    .status(RecoveryStatus.OPEN)
                    .build());
            recoveryCreated = true;
        }

        boolean promptGoogle = rating >= 4 && invitation.getGoogleReviewUrl() != null;
        String thankYou = rating >= 4
                ? "Thank you for your feedback!"
                : "Thank you — your feedback helps us improve.";

        return SubmitPublicReviewResponse.builder()
                .overallRating(rating)
                .promptGoogleReview(promptGoogle)
                .googleReviewUrl(promptGoogle ? invitation.getGoogleReviewUrl() : null)
                .recoveryCreated(recoveryCreated)
                .thankYouMessage(thankYou)
                .build();
    }

    private void expireIfNeeded(ReviewInvitation invitation) {
        if (invitation.getStatus() == ReviewInvitationStatus.PENDING
                && invitation.getExpiresAt().isBefore(Instant.now())) {
            invitation.setStatus(ReviewInvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
        }
    }

    private static List<ReviewCategoryOptionDto> categoryOptions() {
        return Arrays.stream(ReviewCategory.values())
                .map(category -> ReviewCategoryOptionDto.builder()
                        .id(category.name())
                        .label(categoryLabel(category))
                        .build())
                .toList();
    }

    private static String categoryLabel(ReviewCategory category) {
        return switch (category) {
            case SERVICE -> "Service quality";
            case AMBIENCE -> "Ambience";
            case STAFF -> "Staff attitude";
            case CLEANLINESS -> "Cleanliness";
            case VALUE_FOR_MONEY -> "Value for money";
        };
    }

    private static Map<String, Integer> validateCategoryRatings(Map<String, Integer> raw) {
        Map<String, Integer> validated = new LinkedHashMap<>();
        for (ReviewCategory category : ReviewCategory.values()) {
            Integer value = raw != null ? raw.get(category.name()) : null;
            if (value == null || value < 1 || value > 5) {
                throw new BadRequestException("Please rate " + categoryLabel(category).toLowerCase());
            }
            validated.put(category.name(), value);
        }
        return validated;
    }

    private static List<ImprovementTag> parseTags(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(tag -> {
                    try {
                        return ImprovementTag.valueOf(tag);
                    } catch (IllegalArgumentException ex) {
                        return null;
                    }
                })
                .filter(t -> t != null)
                .collect(Collectors.toList());
    }

    private static String serializeTags(List<ImprovementTag> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return tags.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private static String sanitizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.isEmpty() ? null : trimmed.substring(0, Math.min(trimmed.length(), 2000));
    }
}
