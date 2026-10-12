package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.SalaryAdvanceInstallment;
import com.salonplatform.domain.enums.InstallmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SalaryAdvanceInstallmentRepository extends JpaRepository<SalaryAdvanceInstallment, UUID> {

    List<SalaryAdvanceInstallment> findByAdvanceIdOrderByDueMonthAsc(UUID advanceId);

    List<SalaryAdvanceInstallment> findByAdvanceIdInOrderByDueMonthAsc(Collection<UUID> advanceIds);

    long countByAdvanceIdAndStatus(UUID advanceId, InstallmentStatus status);

    List<SalaryAdvanceInstallment> findByTenantIdAndDueMonthAndStatus(
            UUID tenantId, LocalDate dueMonth, InstallmentStatus status);

    List<SalaryAdvanceInstallment> findByTenantIdAndDueMonth(UUID tenantId, LocalDate dueMonth);

    /** Money still to be recovered: scheduled installments of approved (not cancelled/rejected) advances. */
    @Query("select coalesce(sum(i.amount), 0) from SalaryAdvanceInstallment i"
            + " where i.tenantId = :tenantId and i.status = com.salonplatform.domain.enums.InstallmentStatus.SCHEDULED")
    BigDecimal sumOutstanding(@Param("tenantId") UUID tenantId);

    @Query("select coalesce(sum(i.amount), 0) from SalaryAdvanceInstallment i"
            + " where i.staffId = :staffId and i.status = com.salonplatform.domain.enums.InstallmentStatus.SCHEDULED")
    BigDecimal sumOutstandingForStaff(@Param("staffId") UUID staffId);

    @Query("select i from SalaryAdvanceInstallment i where i.tenantId = :tenantId"
            + " and i.status = com.salonplatform.domain.enums.InstallmentStatus.SCHEDULED")
    List<SalaryAdvanceInstallment> findScheduledByTenant(@Param("tenantId") UUID tenantId);
}
