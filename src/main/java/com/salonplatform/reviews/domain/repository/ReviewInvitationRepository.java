package com.salonplatform.reviews.domain.repository;

import com.salonplatform.reviews.domain.entity.ReviewInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReviewInvitationRepository extends JpaRepository<ReviewInvitation, UUID> {
    Optional<ReviewInvitation> findByVisitId(UUID visitId);
}
