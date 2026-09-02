package com.salonplatform.repository;

import com.salonplatform.domain.entity.Booking;
import com.salonplatform.domain.entity.BookingLineItem;
import com.salonplatform.domain.entity.Customer;
import com.salonplatform.domain.entity.Invoice;
import com.salonplatform.domain.entity.MembershipSubscription;
import com.salonplatform.domain.entity.SalonService;
import com.salonplatform.domain.enums.BookingSource;
import com.salonplatform.domain.enums.BookingStatus;
import com.salonplatform.domain.enums.CampaignMembershipFilter;
import com.salonplatform.domain.enums.MembershipStatus;
import com.salonplatform.dto.customer.CustomerListFilter;
import com.salonplatform.reviews.domain.entity.Review;
import com.salonplatform.reviews.domain.entity.ReviewInvitation;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public final class CustomerSpecifications {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private CustomerSpecifications() {}

    public static Specification<Customer> fromFilter(UUID tenantId, CustomerListFilter filter) {
        Specification<Customer> spec = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);

        if (filter.getNames() != null && !filter.getNames().isEmpty()) {
            List<String> names = filter.getNames().stream()
                    .filter(n -> n != null && !n.isBlank())
                    .map(n -> n.trim().toLowerCase())
                    .distinct()
                    .toList();
            if (!names.isEmpty()) {
                spec = spec.and((root, query, cb) -> {
                    Predicate[] ors = names.stream()
                            .map(n -> cb.equal(cb.lower(root.get("name")), n))
                            .toArray(Predicate[]::new);
                    return cb.or(ors);
                });
            }
        } else if (filter.getName() != null && !filter.getName().isBlank()) {
            String q = "%" + filter.getName().trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), q));
        }
        if (filter.getSociety() != null && !filter.getSociety().isBlank()) {
            String q = "%" + filter.getSociety().trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("society")), q));
        }
        if (filter.getPhones() != null && !filter.getPhones().isEmpty()) {
            List<String> phones = filter.getPhones().stream()
                    .filter(p -> p != null && !p.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
            if (!phones.isEmpty()) {
                spec = spec.and((root, query, cb) -> {
                    Predicate[] ors = phones.stream()
                            .map(p -> cb.equal(root.get("phone"), p))
                            .toArray(Predicate[]::new);
                    return cb.or(ors);
                });
            }
        } else if (filter.getPhone() != null && !filter.getPhone().isBlank()) {
            String q = "%" + filter.getPhone().trim() + "%";
            spec = spec.and((root, query, cb) -> cb.like(root.get("phone"), q));
        }
        if (filter.getVisitPassId() != null && !filter.getVisitPassId().isBlank()) {
            String q = "%" + filter.getVisitPassId().trim().toUpperCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.upper(root.get("visitPassId")), q));
        }

        UUID branchId = filter.getBranchId();
        if (branchId != null) {
            if (filter.getMinVisitCount() != null) {
                spec = spec.and(branchVisitCountSpec(tenantId, branchId, filter.getMinVisitCount(), true));
            }
            if (filter.getMaxVisitCount() != null) {
                spec = spec.and(branchVisitCountSpec(tenantId, branchId, filter.getMaxVisitCount(), false));
            }
            if (filter.getMinLifetimeSpend() != null) {
                spec = spec.and(branchLifetimeSpendSpec(tenantId, branchId, filter.getMinLifetimeSpend(), true));
            }
            if (filter.getMaxLifetimeSpend() != null) {
                spec = spec.and(branchLifetimeSpendSpec(tenantId, branchId, filter.getMaxLifetimeSpend(), false));
            }
            if (filter.getLastVisitFrom() != null) {
                Instant start = filter.getLastVisitFrom().atStartOfDay(ZONE).toInstant();
                spec = spec.and(branchLastVisitFromSpec(tenantId, branchId, start));
            }
            if (filter.getLastVisitTo() != null) {
                Instant end = filter.getLastVisitTo().plusDays(1).atStartOfDay(ZONE).toInstant();
                spec = spec.and(branchLastVisitBeforeSpec(tenantId, branchId, end));
            }
        } else {
            if (filter.getMinVisitCount() != null) {
                spec = spec.and((root, query, cb) ->
                        cb.greaterThanOrEqualTo(root.get("visitCount"), filter.getMinVisitCount()));
            }
            if (filter.getMaxVisitCount() != null) {
                spec = spec.and((root, query, cb) ->
                        cb.lessThanOrEqualTo(root.get("visitCount"), filter.getMaxVisitCount()));
            }
            if (filter.getMinLifetimeSpend() != null) {
                spec = spec.and((root, query, cb) ->
                        cb.greaterThanOrEqualTo(root.get("lifetimeSpend"), filter.getMinLifetimeSpend()));
            }
            if (filter.getMaxLifetimeSpend() != null) {
                spec = spec.and((root, query, cb) ->
                        cb.lessThanOrEqualTo(root.get("lifetimeSpend"), filter.getMaxLifetimeSpend()));
            }
            if (filter.getLastVisitFrom() != null) {
                Instant start = filter.getLastVisitFrom().atStartOfDay(ZONE).toInstant();
                spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("lastVisitAt"), start));
            }
            if (filter.getLastVisitTo() != null) {
                Instant end = filter.getLastVisitTo().plusDays(1).atStartOfDay(ZONE).toInstant();
                spec = spec.and((root, query, cb) -> cb.lessThan(root.get("lastVisitAt"), end));
            }
        }

        if (Boolean.TRUE.equals(filter.getWhatsappOptInOnly())) {
            spec = spec.and((root, query, cb) -> cb.isTrue(root.get("whatsappOptIn")));
        }
        if (Boolean.TRUE.equals(filter.getSmsOptInOnly())) {
            spec = spec.and((root, query, cb) -> cb.isTrue(root.get("smsOptIn")));
        }

        if (filter.getMembershipFilter() != null) {
            spec = spec.and(membershipFilterSpec(tenantId, filter.getMembershipFilter(),
                    filter.getMembershipExpiringWithinDays()));
        }

        if (filter.getHasServiceIds() != null && !filter.getHasServiceIds().isEmpty()) {
            spec = spec.and(hasServicesSpec(tenantId, filter.getHasServiceIds(), branchId));
        }
        if (filter.getExcludeServiceIds() != null && !filter.getExcludeServiceIds().isEmpty()) {
            spec = spec.and(excludeServicesSpec(tenantId, filter.getExcludeServiceIds(), branchId));
        }
        if (filter.getHasServiceCategoryIds() != null && !filter.getHasServiceCategoryIds().isEmpty()) {
            spec = spec.and(hasServiceCategoriesSpec(tenantId, filter.getHasServiceCategoryIds(), branchId));
        }
        if (filter.getExcludeServiceCategoryIds() != null && !filter.getExcludeServiceCategoryIds().isEmpty()) {
            spec = spec.and(excludeServiceCategoriesSpec(tenantId, filter.getExcludeServiceCategoryIds(), branchId));
        }

        if (filter.getMaxOverallRating() != null) {
            spec = spec.and(reviewRatingSpec(tenantId, null, filter.getMaxOverallRating()));
        }
        if (filter.getMinOverallRating() != null) {
            spec = spec.and(reviewRatingSpec(tenantId, filter.getMinOverallRating(), null));
        }
        if (filter.getHasSubmittedReview() != null) {
            spec = spec.and(hasReviewSpec(tenantId, filter.getHasSubmittedReview()));
        }
        if (Boolean.TRUE.equals(filter.getGoogleReviewNotSubmitted())) {
            spec = spec.and(googleReviewNotSubmittedSpec(tenantId, filter.getMinOverallRating()));
        }
        if (filter.getBookingSource() != null) {
            spec = spec.and(bookingSourceSpec(tenantId, filter.getBookingSource(), branchId));
        }

        return spec;
    }

    public static Specification<Customer> fromCampaignFilters(
            UUID tenantId,
            List<String> names,
            List<String> phones,
            String society,
            Integer minVisitCount,
            Integer maxVisitCount,
            BigDecimal minLifetimeSpend,
            BigDecimal maxLifetimeSpend,
            LocalDate lastVisitFrom,
            LocalDate lastVisitTo,
            Boolean whatsappOptInOnly,
            Boolean smsOptInOnly,
            UUID branchId,
            CampaignMembershipFilter membershipFilter,
            Integer membershipExpiringWithinDays,
            List<UUID> hasServiceIds,
            List<UUID> excludeServiceIds,
            List<UUID> hasServiceCategoryIds,
            List<UUID> excludeServiceCategoryIds,
            Integer maxOverallRating,
            Integer minOverallRating,
            Boolean hasSubmittedReview,
            Boolean googleReviewNotSubmitted,
            BookingSource bookingSource) {
        return fromFilter(tenantId, CustomerListFilter.builder()
                .names(names)
                .phones(phones)
                .society(society)
                .minVisitCount(minVisitCount)
                .maxVisitCount(maxVisitCount)
                .minLifetimeSpend(minLifetimeSpend)
                .maxLifetimeSpend(maxLifetimeSpend)
                .lastVisitFrom(lastVisitFrom)
                .lastVisitTo(lastVisitTo)
                .whatsappOptInOnly(whatsappOptInOnly)
                .smsOptInOnly(smsOptInOnly)
                .branchId(branchId)
                .membershipFilter(membershipFilter)
                .membershipExpiringWithinDays(membershipExpiringWithinDays)
                .hasServiceIds(hasServiceIds)
                .excludeServiceIds(excludeServiceIds)
                .hasServiceCategoryIds(hasServiceCategoryIds)
                .excludeServiceCategoryIds(excludeServiceCategoryIds)
                .maxOverallRating(maxOverallRating)
                .minOverallRating(minOverallRating)
                .hasSubmittedReview(hasSubmittedReview)
                .googleReviewNotSubmitted(googleReviewNotSubmitted)
                .bookingSource(bookingSource)
                .build());
    }

    private static Specification<Customer> branchVisitCountSpec(
            UUID tenantId, UUID branchId, int threshold, boolean minimum) {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            var booking = sq.from(Booking.class);
            sq.select(cb.count(booking));
            sq.where(
                    cb.equal(booking.get("customerId"), root.get("id")),
                    cb.equal(booking.get("tenantId"), tenantId),
                    cb.equal(booking.get("branchId"), branchId),
                    cb.equal(booking.get("status"), BookingStatus.COMPLETED));
            return minimum
                    ? cb.greaterThanOrEqualTo(sq, (long) threshold)
                    : cb.lessThanOrEqualTo(sq, (long) threshold);
        };
    }

    private static Specification<Customer> branchLifetimeSpendSpec(
            UUID tenantId, UUID branchId, BigDecimal threshold, boolean minimum) {
        return (root, query, cb) -> {
            Subquery<BigDecimal> sq = query.subquery(BigDecimal.class);
            var booking = sq.from(Booking.class);
            var invoice = sq.from(Invoice.class);
            sq.select(cb.coalesce(cb.sum(invoice.get("grandTotal")), BigDecimal.ZERO));
            sq.where(
                    cb.equal(booking.get("customerId"), root.get("id")),
                    cb.equal(booking.get("tenantId"), tenantId),
                    cb.equal(booking.get("branchId"), branchId),
                    cb.equal(booking.get("status"), BookingStatus.COMPLETED),
                    cb.equal(invoice.get("bookingId"), booking.get("id")));
            return minimum
                    ? cb.greaterThanOrEqualTo(sq, threshold)
                    : cb.lessThanOrEqualTo(sq, threshold);
        };
    }

    private static Specification<Customer> branchLastVisitFromSpec(UUID tenantId, UUID branchId, Instant start) {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            var booking = sq.from(Booking.class);
            sq.select(cb.literal(1L));
            sq.where(
                    cb.equal(booking.get("customerId"), root.get("id")),
                    cb.equal(booking.get("tenantId"), tenantId),
                    cb.equal(booking.get("branchId"), branchId),
                    cb.equal(booking.get("status"), BookingStatus.COMPLETED),
                    cb.isNotNull(booking.get("completedAt")),
                    cb.greaterThanOrEqualTo(booking.get("completedAt"), start));
            return cb.exists(sq);
        };
    }

    private static Specification<Customer> branchLastVisitBeforeSpec(UUID tenantId, UUID branchId, Instant end) {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            var booking = sq.from(Booking.class);
            sq.select(cb.literal(1L));
            sq.where(
                    cb.equal(booking.get("customerId"), root.get("id")),
                    cb.equal(booking.get("tenantId"), tenantId),
                    cb.equal(booking.get("branchId"), branchId),
                    cb.equal(booking.get("status"), BookingStatus.COMPLETED),
                    cb.isNotNull(booking.get("completedAt")),
                    cb.greaterThanOrEqualTo(booking.get("completedAt"), end));
            return cb.not(cb.exists(sq));
        };
    }

    private static Specification<Customer> membershipFilterSpec(
            UUID tenantId, CampaignMembershipFilter filter, Integer expiringWithinDays) {
        LocalDate today = LocalDate.now(ZONE);
        return switch (filter) {
            case NON_MEMBER -> (root, query, cb) ->
                    cb.not(cb.exists(activeMembershipExists(tenantId, today, root, query, cb)));
            case ACTIVE -> (root, query, cb) ->
                    cb.exists(activeMembershipExists(tenantId, today, root, query, cb));
            case EXPIRED -> (root, query, cb) -> cb.and(
                    cb.not(cb.exists(activeMembershipExists(tenantId, today, root, query, cb))),
                    anyMembershipExists(tenantId, root, query, cb));
            case EXPIRING_SOON -> {
                int days = expiringWithinDays != null ? expiringWithinDays : 14;
                LocalDate cutoff = today.plusDays(days);
                yield (root, query, cb) -> {
                    Subquery<Long> sq = query.subquery(Long.class);
                    var sub = sq.from(MembershipSubscription.class);
                    sq.select(cb.literal(1L));
                    sq.where(
                            cb.equal(sub.get("customerId"), root.get("id")),
                            cb.equal(sub.get("tenantId"), tenantId),
                            cb.equal(sub.get("status"), MembershipStatus.ACTIVE),
                            cb.greaterThanOrEqualTo(sub.get("endsOn"), today),
                            cb.lessThanOrEqualTo(sub.get("endsOn"), cutoff));
                    return cb.exists(sq);
                };
            }
        };
    }

    private static Subquery<Long> activeMembershipExists(
            UUID tenantId, LocalDate today,
            jakarta.persistence.criteria.Root<Customer> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb) {
        Subquery<Long> sq = query.subquery(Long.class);
        var sub = sq.from(MembershipSubscription.class);
        sq.select(cb.literal(1L));
        sq.where(
                cb.equal(sub.get("customerId"), root.get("id")),
                cb.equal(sub.get("tenantId"), tenantId),
                cb.equal(sub.get("status"), MembershipStatus.ACTIVE),
                cb.greaterThanOrEqualTo(sub.get("endsOn"), today));
        return sq;
    }

    private static jakarta.persistence.criteria.Predicate anyMembershipExists(
            UUID tenantId,
            jakarta.persistence.criteria.Root<Customer> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb) {
        Subquery<Long> sq = query.subquery(Long.class);
        var sub = sq.from(MembershipSubscription.class);
        sq.select(cb.literal(1L));
        sq.where(
                cb.equal(sub.get("customerId"), root.get("id")),
                cb.equal(sub.get("tenantId"), tenantId));
        return cb.exists(sq);
    }

    private static Specification<Customer> hasServicesSpec(UUID tenantId, List<UUID> serviceIds, UUID branchId) {
        return (root, query, cb) -> completedServiceLineExists(tenantId, serviceIds, branchId, root, query, cb, true);
    }

    private static Specification<Customer> excludeServicesSpec(UUID tenantId, List<UUID> serviceIds, UUID branchId) {
        return (root, query, cb) -> cb.not(
                completedServiceLineExists(tenantId, serviceIds, branchId, root, query, cb, true));
    }

    private static jakarta.persistence.criteria.Predicate completedServiceLineExists(
            UUID tenantId,
            List<UUID> serviceIds,
            UUID branchId,
            jakarta.persistence.criteria.Root<Customer> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            boolean matchAny) {
        Subquery<Long> sq = query.subquery(Long.class);
        var booking = sq.from(Booking.class);
        var line = sq.from(BookingLineItem.class);
        sq.select(cb.literal(1L));
        Predicate serviceMatch = line.get("serviceId").in(serviceIds);
        Predicate where = cb.and(
                cb.equal(booking.get("customerId"), root.get("id")),
                cb.equal(booking.get("tenantId"), tenantId),
                cb.equal(booking.get("status"), BookingStatus.COMPLETED),
                cb.equal(line.get("bookingId"), booking.get("id")),
                serviceMatch);
        if (branchId != null) {
            where = cb.and(where, cb.equal(booking.get("branchId"), branchId));
        }
        sq.where(where);
        return cb.exists(sq);
    }

    private static Specification<Customer> hasServiceCategoriesSpec(
            UUID tenantId, List<UUID> categoryIds, UUID branchId) {
        return (root, query, cb) -> completedCategoryLineExists(tenantId, categoryIds, branchId, root, query, cb);
    }

    private static Specification<Customer> excludeServiceCategoriesSpec(
            UUID tenantId, List<UUID> categoryIds, UUID branchId) {
        return (root, query, cb) -> cb.not(
                completedCategoryLineExists(tenantId, categoryIds, branchId, root, query, cb));
    }

    private static jakarta.persistence.criteria.Predicate completedCategoryLineExists(
            UUID tenantId,
            List<UUID> categoryIds,
            UUID branchId,
            jakarta.persistence.criteria.Root<Customer> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb) {
        Subquery<Long> sq = query.subquery(Long.class);
        var booking = sq.from(Booking.class);
        var line = sq.from(BookingLineItem.class);
        var svc = sq.from(SalonService.class);
        sq.select(cb.literal(1L));
        Predicate where = cb.and(
                cb.equal(booking.get("customerId"), root.get("id")),
                cb.equal(booking.get("tenantId"), tenantId),
                cb.equal(booking.get("status"), BookingStatus.COMPLETED),
                cb.equal(line.get("bookingId"), booking.get("id")),
                cb.equal(svc.get("id"), line.get("serviceId")),
                svc.get("categoryId").in(categoryIds));
        if (branchId != null) {
            where = cb.and(where, cb.equal(booking.get("branchId"), branchId));
        }
        sq.where(where);
        return cb.exists(sq);
    }

    private static Specification<Customer> reviewRatingSpec(
            UUID tenantId, Integer minRating, Integer maxRating) {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            var review = sq.from(Review.class);
            var invitation = sq.from(ReviewInvitation.class);
            sq.select(cb.literal(1L));
            Predicate ratingPredicate = cb.conjunction();
            if (minRating != null) {
                ratingPredicate = cb.and(ratingPredicate, cb.greaterThanOrEqualTo(review.get("overallRating"), minRating));
            }
            if (maxRating != null) {
                ratingPredicate = cb.and(ratingPredicate, cb.lessThanOrEqualTo(review.get("overallRating"), maxRating));
            }
            sq.where(
                    cb.equal(invitation.get("customerId"), root.get("id")),
                    cb.equal(review.get("invitationId"), invitation.get("id")),
                    cb.equal(review.get("tenantId"), tenantId),
                    ratingPredicate);
            return cb.exists(sq);
        };
    }

    private static Specification<Customer> hasReviewSpec(UUID tenantId, boolean hasReview) {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            var review = sq.from(Review.class);
            var invitation = sq.from(ReviewInvitation.class);
            sq.select(cb.literal(1L));
            sq.where(
                    cb.equal(invitation.get("customerId"), root.get("id")),
                    cb.equal(review.get("invitationId"), invitation.get("id")),
                    cb.equal(review.get("tenantId"), tenantId));
            return hasReview ? cb.exists(sq) : cb.not(cb.exists(sq));
        };
    }

    private static Specification<Customer> googleReviewNotSubmittedSpec(UUID tenantId, Integer minRating) {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            var review = sq.from(Review.class);
            var invitation = sq.from(ReviewInvitation.class);
            sq.select(cb.literal(1L));
            int floor = minRating != null ? minRating : 4;
            sq.where(
                    cb.equal(invitation.get("customerId"), root.get("id")),
                    cb.equal(review.get("invitationId"), invitation.get("id")),
                    cb.equal(review.get("tenantId"), tenantId),
                    cb.greaterThanOrEqualTo(review.get("overallRating"), floor),
                    cb.isFalse(review.get("googleReviewRedirected")));
            return cb.exists(sq);
        };
    }

    private static Specification<Customer> bookingSourceSpec(
            UUID tenantId, BookingSource source, UUID branchId) {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            var booking = sq.from(Booking.class);
            sq.select(cb.literal(1L));
            Predicate where = cb.and(
                    cb.equal(booking.get("customerId"), root.get("id")),
                    cb.equal(booking.get("tenantId"), tenantId),
                    cb.equal(booking.get("status"), BookingStatus.COMPLETED),
                    cb.equal(booking.get("source"), source));
            if (branchId != null) {
                where = cb.and(where, cb.equal(booking.get("branchId"), branchId));
            }
            sq.where(where);
            return cb.exists(sq);
        };
    }
}
