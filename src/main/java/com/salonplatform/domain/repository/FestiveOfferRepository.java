package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.FestiveOffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FestiveOfferRepository extends JpaRepository<FestiveOffer, UUID> {
    List<FestiveOffer> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId);

    Optional<FestiveOffer> findByIdAndTenantId(UUID id, UUID tenantId);
}
