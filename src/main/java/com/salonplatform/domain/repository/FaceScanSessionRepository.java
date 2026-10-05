package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.FaceScanSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FaceScanSessionRepository extends JpaRepository<FaceScanSession, UUID> {

    Page<FaceScanSession> findByTenantIdAndBranchIdOrderByCreatedAtDesc(
            UUID tenantId, UUID branchId, Pageable pageable);

    Page<FaceScanSession> findByTenantIdAndBranchIdAndCustomerIdOrderByCreatedAtDesc(
            UUID tenantId, UUID branchId, UUID customerId, Pageable pageable);
}
