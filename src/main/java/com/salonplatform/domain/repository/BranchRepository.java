package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.enums.BranchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchRepository extends JpaRepository<Branch, UUID> {
    List<Branch> findByTenantId(UUID tenantId);
    List<Branch> findByTenantIdAndStatus(UUID tenantId, BranchStatus status);
    Optional<Branch> findByTenantIdAndCode(UUID tenantId, String code);
}
