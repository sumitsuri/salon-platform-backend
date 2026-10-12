package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.SalaryAdvancePolicy;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SalaryAdvancePolicyRepository extends JpaRepository<SalaryAdvancePolicy, UUID> {
    Optional<SalaryAdvancePolicy> findByTenantId(UUID tenantId);

    /** Serialises concurrent approvals for one brand so two requests cannot both spend the last of the budget. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SalaryAdvancePolicy p where p.tenantId = :tenantId")
    Optional<SalaryAdvancePolicy> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);
}
