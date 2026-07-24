package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.LocalCompetitor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LocalCompetitorRepository extends JpaRepository<LocalCompetitor, UUID> {
    List<LocalCompetitor> findByTenantIdAndActiveTrueOrderByNameAsc(UUID tenantId);

    List<LocalCompetitor> findByTenantIdAndBranchIdAndActiveTrueOrderByNameAsc(UUID tenantId, UUID branchId);

    long countByTenantIdAndActiveTrue(UUID tenantId);
}
