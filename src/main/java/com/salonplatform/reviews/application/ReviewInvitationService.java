package com.salonplatform.reviews.application;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.Customer;
import com.salonplatform.domain.entity.Invoice;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.reviews.config.ReviewsProperties;
import com.salonplatform.reviews.domain.entity.Review;
import com.salonplatform.reviews.domain.entity.ReviewInvitation;
import com.salonplatform.reviews.domain.enums.ReviewInvitationStatus;
import com.salonplatform.reviews.domain.repository.ReviewInvitationRepository;
import com.salonplatform.reviews.domain.repository.ReviewRepository;
import com.salonplatform.reviews.dto.ReviewInvitationDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewInvitationService {

    private final ReviewInvitationRepository invitationRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewTokenService tokenService;
    private final ReviewsProperties reviewsProperties;

    @Transactional
    public ReviewInvitationDto createAfterPayment(Invoice invoice, Branch branch, Customer customer) {
        Optional<ReviewInvitation> existing = invitationRepository.findByVisitId(invoice.getBookingId());
        if (existing.isPresent()) {
            return toDto(existing.get());
        }

        Instant expiresAt = Instant.now().plus(reviewsProperties.getTokenExpiryDays(), ChronoUnit.DAYS);
        ReviewInvitation invitation = invitationRepository.save(ReviewInvitation.builder()
                .tenantId(invoice.getTenantId())
                .branchId(invoice.getBranchId())
                .branchName(branch.getName())
                .visitId(invoice.getBookingId())
                .invoiceId(invoice.getId())
                .customerId(customer.getId())
                .customerFirstName(firstName(customer.getName()))
                .googleReviewUrl(blankToNull(branch.getGoogleReviewUrl()))
                .status(ReviewInvitationStatus.PENDING)
                .expiresAt(expiresAt)
                .build());

        return toDto(invitation);
    }

    @Transactional(readOnly = true)
    public ReviewInvitationDto getByVisitId(UUID visitId) {
        ReviewInvitation invitation = invitationRepository.findByVisitId(visitId)
                .orElseThrow(() -> new BadRequestException("Review invitation not found"));
        return toDto(invitation);
    }

    private ReviewInvitationDto toDto(ReviewInvitation invitation) {
        refreshExpiredStatus(invitation);
        String token = tokenService.createToken(invitation.getId());
        String reviewUrl = UriComponentsBuilder
                .fromHttpUrl(reviewsProperties.getPublicFrontendBaseUrl().replaceAll("/$", "") + "/review")
                .queryParam("token", token)
                .toUriString();
        Integer submittedRating = reviewRepository.findByVisitId(invitation.getVisitId())
                .map(Review::getOverallRating)
                .orElse(null);

        return ReviewInvitationDto.builder()
                .invitationId(invitation.getId())
                .visitId(invitation.getVisitId())
                .token(token)
                .reviewUrl(reviewUrl)
                .status(invitation.getStatus().name())
                .expiresAt(invitation.getExpiresAt())
                .submittedRating(submittedRating)
                .build();
    }

    private void refreshExpiredStatus(ReviewInvitation invitation) {
        if (invitation.getStatus() == ReviewInvitationStatus.PENDING
                && invitation.getExpiresAt().isBefore(Instant.now())) {
            invitation.setStatus(ReviewInvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
        }
    }

    private static String firstName(String name) {
        if (name == null || name.isBlank()) {
            return "Guest";
        }
        String trimmed = name.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
