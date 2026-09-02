package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.MessageDeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageDeliveryLogRepository extends JpaRepository<MessageDeliveryLog, UUID> {

    List<MessageDeliveryLog> findByTenantIdAndCampaignIdOrderByCreatedAtDesc(UUID tenantId, UUID campaignId);

    List<MessageDeliveryLog> findByTenantIdAndCampaignRunIdOrderByCreatedAtDesc(UUID tenantId, UUID campaignRunId);
}
