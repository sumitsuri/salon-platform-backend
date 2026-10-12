package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.SalaryAdvance;
import com.salonplatform.domain.enums.AdvanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SalaryAdvanceRepository extends JpaRepository<SalaryAdvance, UUID> {

    List<SalaryAdvance> findByStaffIdOrderByCreatedAtDesc(UUID staffId);

    List<SalaryAdvance> findByTenantId(UUID tenantId);

    List<SalaryAdvance> findByTenantIdAndStatus(UUID tenantId, AdvanceStatus status);

    boolean existsByStaffIdAndStatus(UUID staffId, AdvanceStatus status);

    @Query("select a from SalaryAdvance a where a.tenantId = :tenantId"
            + " and (:status is null or a.status = :status)"
            + " and (:staffId is null or a.staffId = :staffId)"
            + " and (:branchId is null or a.branchId = :branchId)")
    Page<SalaryAdvance> search(
            @Param("tenantId") UUID tenantId,
            @Param("status") AdvanceStatus status,
            @Param("staffId") UUID staffId,
            @Param("branchId") UUID branchId,
            Pageable pageable);

    @Query("select coalesce(sum(a.requestedAmount), 0) from SalaryAdvance a"
            + " where a.tenantId = :tenantId and a.status = com.salonplatform.domain.enums.AdvanceStatus.PENDING")
    BigDecimal sumPendingRequested(@Param("tenantId") UUID tenantId);

    @Query("select a from SalaryAdvance a where a.staffId in :staffIds")
    List<SalaryAdvance> findByStaffIdIn(@Param("staffIds") Collection<UUID> staffIds);
}
