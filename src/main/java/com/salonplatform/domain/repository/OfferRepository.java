package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.Offer;
import com.salonplatform.domain.enums.PromoStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OfferRepository extends JpaRepository<Offer, UUID> {
    List<Offer> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<Offer> findByTenantIdAndStatus(UUID tenantId, PromoStatus status);
}
