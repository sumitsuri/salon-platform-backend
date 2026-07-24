package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.MarketingCampaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MarketingCampaignRepository extends JpaRepository<MarketingCampaign, UUID> {
    List<MarketingCampaign> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
