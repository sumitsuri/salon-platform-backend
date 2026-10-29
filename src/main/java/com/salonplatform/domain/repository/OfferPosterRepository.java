package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.OfferPoster;
import com.salonplatform.domain.enums.OfferPosterStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfferPosterRepository extends JpaRepository<OfferPoster, UUID> {
    List<OfferPoster> findByOfferIdAndTenantIdOrderByCreatedAtDesc(UUID offerId, UUID tenantId);

    List<OfferPoster> findByOfferIdInAndTenantIdOrderByCreatedAtDesc(Collection<UUID> offerIds, UUID tenantId);

    Optional<OfferPoster> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantIdAndCreatedAtGreaterThanEqual(UUID tenantId, Instant since);

    boolean existsByOfferIdAndStatus(UUID offerId, OfferPosterStatus status);

    List<OfferPoster> findByOfferId(UUID offerId);

    void deleteByOfferId(UUID offerId);
}
