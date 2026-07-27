package com.salonplatform.sales.domain.repository;

import com.salonplatform.sales.domain.entity.SalesActivity;
import com.salonplatform.sales.domain.enums.ActivityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SalesActivityRepository extends JpaRepository<SalesActivity, UUID> {
    List<SalesActivity> findByLeadIdOrderByCreatedAtDesc(UUID leadId);

    List<SalesActivity> findByRepIdAndActivityTypeAndCreatedAtBetween(
            UUID repId, ActivityType activityType, Instant start, Instant end);
}
