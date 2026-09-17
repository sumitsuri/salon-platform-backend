package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.ScratchCampaign;
import com.salonplatform.domain.enums.PromoStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScratchCampaignRepository extends JpaRepository<ScratchCampaign, UUID> {

    List<ScratchCampaign> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<ScratchCampaign> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ScratchCampaign> findByTenantIdAndStatus(UUID tenantId, PromoStatus status);
}
