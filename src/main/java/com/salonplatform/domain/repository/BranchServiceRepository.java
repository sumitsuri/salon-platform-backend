package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.BranchService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchServiceRepository extends JpaRepository<BranchService, UUID> {
    List<BranchService> findByBranchIdAndActiveTrue(UUID branchId);
    List<BranchService> findByTenantIdAndBranchId(UUID tenantId, UUID branchId);
    Optional<BranchService> findByBranchIdAndServiceId(UUID branchId, UUID serviceId);
}
