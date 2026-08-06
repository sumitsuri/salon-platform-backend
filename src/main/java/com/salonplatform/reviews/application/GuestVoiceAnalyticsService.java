package com.salonplatform.reviews.application;

import com.salonplatform.reviews.domain.entity.Review;
import com.salonplatform.reviews.domain.entity.ReviewInvitation;
import com.salonplatform.reviews.domain.entity.ReviewRecovery;
import com.salonplatform.reviews.domain.enums.ImprovementTag;
import com.salonplatform.reviews.domain.enums.RecoveryStatus;
import com.salonplatform.reviews.domain.enums.ReviewCategory;
import com.salonplatform.reviews.domain.repository.ReviewInvitationRepository;
import com.salonplatform.reviews.domain.repository.ReviewRecoveryRepository;
import com.salonplatform.reviews.domain.repository.ReviewRepository;
import com.salonplatform.reviews.dto.GuestVoiceReviewItemDto;
import com.salonplatform.reviews.dto.GuestVoiceSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GuestVoiceAnalyticsService {

    private final ReviewRepository reviewRepository;
    private final ReviewRecoveryRepository recoveryRepository;
    private final ReviewInvitationRepository invitationRepository;

    @Transactional(readOnly = true)
    public GuestVoiceSummaryDto summarize(
            UUID tenantId,
            List<UUID> branchIds,
            Instant from,
            Instant to) {

        List<Review> reviews = branchIds == null || branchIds.isEmpty()
                ? reviewRepository.findForAnalytics(tenantId, from, to)
                : reviewRepository.findForAnalyticsByBranches(tenantId, branchIds, from, to);

        Map<Integer, Long> distribution = new TreeMap<>();
        for (int i = 1; i <= 5; i++) {
            distribution.put(i, 0L);
        }
        Map<String, Long> tagCounts = new LinkedHashMap<>();
        for (ImprovementTag tag : ImprovementTag.values()) {
            tagCounts.put(tag.name(), 0L);
        }
        Map<String, Double> categorySums = new LinkedHashMap<>();
        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        for (ReviewCategory category : ReviewCategory.values()) {
            categorySums.put(category.name(), 0.0);
            categoryCounts.put(category.name(), 0L);
        }

        long promoters = 0;
        long detractors = 0;
        double sum = 0;

        for (Review review : reviews) {
            sum += review.getOverallRating();
            distribution.merge(review.getOverallRating(), 1L, Long::sum);
            if (review.getOverallRating() >= 4) {
                promoters++;
            } else if (review.getOverallRating() <= 3) {
                detractors++;
            }
            parseTags(review.getImprovementTags()).forEach(tag -> tagCounts.merge(tag.name(), 1L, Long::sum));
            accumulateCategory(categorySums, categoryCounts, ReviewCategory.SERVICE.name(), review.getServiceRating());
            accumulateCategory(categorySums, categoryCounts, ReviewCategory.AMBIENCE.name(), review.getAmbienceRating());
            accumulateCategory(categorySums, categoryCounts, ReviewCategory.STAFF.name(), review.getStaffRating());
            accumulateCategory(categorySums, categoryCounts, ReviewCategory.CLEANLINESS.name(), review.getCleanlinessRating());
            accumulateCategory(categorySums, categoryCounts, ReviewCategory.VALUE_FOR_MONEY.name(), review.getValueRating());
        }

        Map<String, Double> categoryAverageRatings = new LinkedHashMap<>();
        for (ReviewCategory category : ReviewCategory.values()) {
            String key = category.name();
            long count = categoryCounts.getOrDefault(key, 0L);
            categoryAverageRatings.put(key, count == 0 ? 0 : categorySums.get(key) / count);
        }

        Map<UUID, ReviewInvitation> invitationsById = loadInvitations(reviews);
        List<GuestVoiceReviewItemDto> reviewItems = reviews.stream()
                .sorted(Comparator.comparing(Review::getSubmittedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(review -> toReviewItem(review, invitationsById.get(review.getInvitationId())))
                .toList();

        List<ReviewRecovery> recoveries = branchIds == null || branchIds.isEmpty()
                ? recoveryRepository.findOpenByTenant(tenantId, RecoveryStatus.OPEN)
                : recoveryRepository.findOpenByTenantAndBranches(tenantId, branchIds, RecoveryStatus.OPEN);

        Map<UUID, Review> reviewsById = reviews.stream()
                .collect(Collectors.toMap(Review::getId, r -> r, (a, b) -> a));
        List<UUID> missingReviewIds = recoveries.stream()
                .map(ReviewRecovery::getReviewId)
                .filter(id -> !reviewsById.containsKey(id))
                .distinct()
                .toList();
        if (!missingReviewIds.isEmpty()) {
            reviewRepository.findAllById(missingReviewIds).forEach(r -> reviewsById.put(r.getId(), r));
        }

        Set<UUID> visitIds = new HashSet<>();
        recoveries.forEach(rr -> visitIds.add(rr.getVisitId()));
        reviewsById.values().forEach(r -> visitIds.add(r.getVisitId()));
        Map<UUID, ReviewInvitation> invitationsByVisit = new HashMap<>();
        for (Review review : reviews) {
            ReviewInvitation invitation = invitationsById.get(review.getInvitationId());
            if (invitation != null) {
                invitationsByVisit.put(invitation.getVisitId(), invitation);
            }
        }
        for (ReviewRecovery recovery : recoveries) {
            if (!invitationsByVisit.containsKey(recovery.getVisitId())) {
                invitationRepository.findByVisitId(recovery.getVisitId())
                        .ifPresent(inv -> invitationsByVisit.put(inv.getVisitId(), inv));
            }
        }

        List<GuestVoiceSummaryDto.RecoveryItemDto> openRecoveries = recoveries.stream()
                .map(rr -> {
                    Review linked = reviewsById.get(rr.getReviewId());
                    ReviewInvitation invitation = invitationsByVisit.get(rr.getVisitId());
                    return GuestVoiceSummaryDto.RecoveryItemDto.builder()
                            .recoveryId(rr.getId())
                            .visitId(rr.getVisitId())
                            .branchId(rr.getBranchId())
                            .branchName(invitation != null ? invitation.getBranchName() : null)
                            .customerFirstName(invitation != null ? invitation.getCustomerFirstName() : null)
                            .overallRating(rr.getOverallRating())
                            .status(rr.getStatus().name())
                            .improvementTags(linked != null
                                    ? parseTags(linked.getImprovementTags()).stream().map(Enum::name).toList()
                                    : List.of())
                            .comment(linked != null ? linked.getComment() : null)
                            .createdAt(rr.getCreatedAt())
                            .build();
                })
                .toList();

        return GuestVoiceSummaryDto.builder()
                .averageRating(reviews.isEmpty() ? 0 : sum / reviews.size())
                .totalReviews(reviews.size())
                .promotersCount(promoters)
                .detractorsCount(detractors)
                .ratingDistribution(distribution)
                .improvementTagCounts(tagCounts)
                .categoryAverageRatings(categoryAverageRatings)
                .reviews(reviewItems)
                .openRecoveries(openRecoveries)
                .build();
    }

    private Map<UUID, ReviewInvitation> loadInvitations(List<Review> reviews) {
        if (reviews.isEmpty()) {
            return Map.of();
        }
        List<UUID> invitationIds = reviews.stream().map(Review::getInvitationId).distinct().toList();
        return invitationRepository.findAllById(invitationIds).stream()
                .collect(Collectors.toMap(ReviewInvitation::getId, i -> i));
    }

    private static GuestVoiceReviewItemDto toReviewItem(Review review, ReviewInvitation invitation) {
        Map<String, Integer> categoryRatings = new LinkedHashMap<>();
        putCategoryRating(categoryRatings, ReviewCategory.SERVICE.name(), review.getServiceRating());
        putCategoryRating(categoryRatings, ReviewCategory.AMBIENCE.name(), review.getAmbienceRating());
        putCategoryRating(categoryRatings, ReviewCategory.STAFF.name(), review.getStaffRating());
        putCategoryRating(categoryRatings, ReviewCategory.CLEANLINESS.name(), review.getCleanlinessRating());
        putCategoryRating(categoryRatings, ReviewCategory.VALUE_FOR_MONEY.name(), review.getValueRating());

        return GuestVoiceReviewItemDto.builder()
                .reviewId(review.getId())
                .visitId(review.getVisitId())
                .branchId(review.getBranchId())
                .branchName(invitation != null ? invitation.getBranchName() : null)
                .customerFirstName(invitation != null ? invitation.getCustomerFirstName() : "Guest")
                .overallRating(review.getOverallRating())
                .categoryRatings(categoryRatings)
                .improvementTags(parseTags(review.getImprovementTags()).stream().map(Enum::name).toList())
                .comment(review.getComment())
                .submittedAt(review.getSubmittedAt())
                .googleReviewRedirected(review.isGoogleReviewRedirected())
                .build();
    }

    private static void putCategoryRating(Map<String, Integer> map, String key, Integer value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static void accumulateCategory(
            Map<String, Double> sums,
            Map<String, Long> counts,
            String key,
            Integer rating) {
        if (rating == null) {
            return;
        }
        sums.merge(key, rating.doubleValue(), Double::sum);
        counts.merge(key, 1L, Long::sum);
    }

    private static List<ImprovementTag> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(tag -> {
                    try {
                        return ImprovementTag.valueOf(tag);
                    } catch (IllegalArgumentException ex) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
