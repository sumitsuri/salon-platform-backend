package com.salonplatform.reviews.domain.repository;

import com.salonplatform.reviews.domain.entity.ReviewRecovery;
import com.salonplatform.reviews.domain.enums.RecoveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReviewRecoveryRepository extends JpaRepository<ReviewRecovery, UUID> {

    @Query("""
            SELECT rr FROM ReviewRecovery rr
            WHERE rr.tenantId = :tenantId
              AND rr.status = :status
            ORDER BY rr.createdAt DESC
            """)
    List<ReviewRecovery> findOpenByTenant(
            @Param("tenantId") UUID tenantId,
            @Param("status") RecoveryStatus status);

    @Query("""
            SELECT rr FROM ReviewRecovery rr
            WHERE rr.tenantId = :tenantId
              AND rr.branchId IN :branchIds
              AND rr.status = :status
            ORDER BY rr.createdAt DESC
            """)
    List<ReviewRecovery> findOpenByTenantAndBranches(
            @Param("tenantId") UUID tenantId,
            @Param("branchIds") List<UUID> branchIds,
            @Param("status") RecoveryStatus status);
}
