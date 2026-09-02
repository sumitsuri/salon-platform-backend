package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.CampaignRun;
import com.salonplatform.domain.enums.CampaignRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CampaignRunRepository extends JpaRepository<CampaignRun, UUID> {
    List<CampaignRun> findByTenantIdAndCampaignIdOrderByStartedAtDesc(UUID tenantId, UUID campaignId);

    boolean existsByCampaignIdAndStatus(UUID campaignId, CampaignRunStatus status);

    long countByCampaignId(UUID campaignId);
}
