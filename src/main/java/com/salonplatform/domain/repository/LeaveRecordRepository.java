package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.LeaveRecord;
import com.salonplatform.domain.enums.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LeaveRecordRepository extends JpaRepository<LeaveRecord, UUID>, JpaSpecificationExecutor<LeaveRecord> {
    List<LeaveRecord> findByTenantIdAndBranchIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            UUID tenantId, UUID branchId, LocalDate end, LocalDate start);

    List<LeaveRecord> findByTenantIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            UUID tenantId, LocalDate end, LocalDate start);

    List<LeaveRecord> findByTenantIdAndBranchIdInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            UUID tenantId, List<UUID> branchIds, LocalDate end, LocalDate start);

    List<LeaveRecord> findByTenantIdAndStatus(UUID tenantId, LeaveStatus status);

    boolean existsByStaffIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            UUID staffId, LeaveStatus status, LocalDate end, LocalDate start);
}
