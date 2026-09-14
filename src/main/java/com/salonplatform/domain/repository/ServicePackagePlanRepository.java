package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.ServicePackagePlan;
import com.salonplatform.domain.enums.PromoStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServicePackagePlanRepository extends JpaRepository<ServicePackagePlan, UUID> {

    List<ServicePackagePlan> findByTenantIdOrderByPredefinedRankAscCreatedAtDesc(UUID tenantId);

    List<ServicePackagePlan> findByTenantIdAndStatusOrderByPredefinedRankAscNameAsc(UUID tenantId, PromoStatus status);

    List<ServicePackagePlan> findByTenantIdAndPredefinedRankIsNotNullOrderByPredefinedRankAsc(UUID tenantId);

    Optional<ServicePackagePlan> findByTenantIdAndPredefinedRank(UUID tenantId, Integer predefinedRank);
}
