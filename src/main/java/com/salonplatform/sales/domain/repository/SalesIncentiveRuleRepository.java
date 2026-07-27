package com.salonplatform.sales.domain.repository;

import com.salonplatform.sales.domain.entity.SalesIncentiveRule;
import com.salonplatform.sales.domain.enums.IncentiveEventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SalesIncentiveRuleRepository extends JpaRepository<SalesIncentiveRule, UUID> {
    List<SalesIncentiveRule> findByActiveTrue();

    List<SalesIncentiveRule> findByEventTypeAndActiveTrue(IncentiveEventType eventType);
}
