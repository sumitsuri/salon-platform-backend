package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.ScalpScanSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ScalpScanSessionRepository extends JpaRepository<ScalpScanSession, UUID> {

    Page<ScalpScanSession> findByTenantIdAndBranchIdOrderByCreatedAtDesc(
            UUID tenantId, UUID branchId, Pageable pageable);

    Page<ScalpScanSession> findByTenantIdAndBranchIdAndCustomerIdOrderByCreatedAtDesc(
            UUID tenantId, UUID branchId, UUID customerId, Pageable pageable);
}
