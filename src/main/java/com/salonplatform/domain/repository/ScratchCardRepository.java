package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.ScratchCard;
import com.salonplatform.domain.enums.ScratchCardStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScratchCardRepository extends JpaRepository<ScratchCard, UUID> {

    Optional<ScratchCard> findByPublicToken(String publicToken);

    Optional<ScratchCard> findByTenantIdAndRedemptionCodeIgnoreCase(UUID tenantId, String redemptionCode);

    long countByCampaignId(UUID campaignId);

    long countByCampaignIdAndStatus(UUID campaignId, ScratchCardStatus status);

    Optional<ScratchCard> findTopByTenantIdAndBookingIdOrderByCreatedAtAsc(UUID tenantId, UUID bookingId);

    Optional<ScratchCard> findTopByTenantIdAndBookingIdAndStatus(UUID tenantId, UUID bookingId, ScratchCardStatus status);
}
