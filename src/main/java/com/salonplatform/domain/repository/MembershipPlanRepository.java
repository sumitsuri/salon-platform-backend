package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.MembershipPlan;
import com.salonplatform.domain.enums.PromoStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, UUID> {
    List<MembershipPlan> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<MembershipPlan> findByTenantIdAndStatus(UUID tenantId, PromoStatus status);
}
