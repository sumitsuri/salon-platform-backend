package com.salonplatform.sales.domain.repository;

import com.salonplatform.sales.domain.entity.SalesIncentiveLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SalesIncentiveLedgerRepository extends JpaRepository<SalesIncentiveLedger, UUID> {
    List<SalesIncentiveLedger> findByRepIdAndWeekStartDate(UUID repId, LocalDate weekStartDate);

    List<SalesIncentiveLedger> findByWeekStartDate(LocalDate weekStartDate);

    boolean existsByLeadIdAndEventType(UUID leadId, com.salonplatform.sales.domain.enums.IncentiveEventType eventType);
}
