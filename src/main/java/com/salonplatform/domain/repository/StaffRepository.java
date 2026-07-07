package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.Staff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StaffRepository extends JpaRepository<Staff, UUID> {
    List<Staff> findByTenantIdAndBranchIdAndActiveTrue(UUID tenantId, UUID branchId);
    List<Staff> findByTenantId(UUID tenantId);
    List<Staff> findByTenantIdAndBranchId(UUID tenantId, UUID branchId);
}
