package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID>, JpaSpecificationExecutor<AttendanceRecord> {
    Optional<AttendanceRecord> findByStaffIdAndWorkDate(UUID staffId, LocalDate workDate);

    List<AttendanceRecord> findByTenantIdAndBranchIdAndWorkDateBetweenOrderByEntryTimeDesc(
            UUID tenantId, UUID branchId, LocalDate start, LocalDate end);

    List<AttendanceRecord> findByTenantIdAndWorkDateBetweenOrderByEntryTimeDesc(
            UUID tenantId, LocalDate start, LocalDate end);

    List<AttendanceRecord> findByTenantIdAndBranchIdInAndWorkDateBetween(
            UUID tenantId, List<UUID> branchIds, LocalDate start, LocalDate end);
}
