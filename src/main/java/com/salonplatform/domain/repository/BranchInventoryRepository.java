package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.BranchInventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchInventoryRepository extends JpaRepository<BranchInventory, UUID> {
    List<BranchInventory> findByTenantIdAndBranchId(UUID tenantId, UUID branchId);
    List<BranchInventory> findByTenantId(UUID tenantId);
    Optional<BranchInventory> findByTenantIdAndBranchIdAndProductId(UUID tenantId, UUID branchId, UUID productId);
}
