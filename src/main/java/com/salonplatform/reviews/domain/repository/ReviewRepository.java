package com.salonplatform.reviews.domain.repository;

import com.salonplatform.reviews.domain.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    Optional<Review> findByVisitId(UUID visitId);

    Optional<Review> findByInvitationId(UUID invitationId);

    @Query("""
            SELECT r FROM Review r
            WHERE r.tenantId = :tenantId
              AND r.submittedAt >= :from
              AND r.submittedAt < :to
            """)
    List<Review> findForAnalytics(
            @Param("tenantId") UUID tenantId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            SELECT r FROM Review r
            WHERE r.tenantId = :tenantId
              AND r.branchId IN :branchIds
              AND r.submittedAt >= :from
              AND r.submittedAt < :to
            """)
    List<Review> findForAnalyticsByBranches(
            @Param("tenantId") UUID tenantId,
            @Param("branchIds") List<UUID> branchIds,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
