package com.salonplatform.sales.domain.repository;

import com.salonplatform.sales.domain.entity.SalesStageHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SalesStageHistoryRepository extends JpaRepository<SalesStageHistory, UUID> {
    List<SalesStageHistory> findByLeadIdOrderByCreatedAtDesc(UUID leadId);
}
