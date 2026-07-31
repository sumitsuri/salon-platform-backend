package com.salonplatform.reviews.application;

import com.salonplatform.reviews.domain.entity.Review;
import com.salonplatform.reviews.domain.entity.ReviewRecovery;
import com.salonplatform.reviews.domain.enums.ImprovementTag;
import com.salonplatform.reviews.domain.enums.RecoveryStatus;
import com.salonplatform.reviews.domain.repository.ReviewRecoveryRepository;
import com.salonplatform.reviews.domain.repository.ReviewRepository;
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
        }

        List<ReviewRecovery> recoveries = branchIds == null || branchIds.isEmpty()
                ? recoveryRepository.findOpenByTenant(tenantId, RecoveryStatus.OPEN)
                : recoveryRepository.findOpenByTenantAndBranches(tenantId, branchIds, RecoveryStatus.OPEN);

        List<GuestVoiceSummaryDto.RecoveryItemDto> openRecoveries = recoveries.stream()
                .map(rr -> GuestVoiceSummaryDto.RecoveryItemDto.builder()
                        .recoveryId(rr.getId())
                        .visitId(rr.getVisitId())
                        .branchId(rr.getBranchId())
                        .overallRating(rr.getOverallRating())
                        .status(rr.getStatus().name())
                        .createdAt(rr.getCreatedAt())
                        .build())
                .toList();

        return GuestVoiceSummaryDto.builder()
                .averageRating(reviews.isEmpty() ? 0 : sum / reviews.size())
                .totalReviews(reviews.size())
                .promotersCount(promoters)
                .detractorsCount(detractors)
                .ratingDistribution(distribution)
                .improvementTagCounts(tagCounts)
                .openRecoveries(openRecoveries)
                .build();
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
