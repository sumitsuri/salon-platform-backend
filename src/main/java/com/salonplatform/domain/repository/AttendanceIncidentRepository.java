package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.AttendanceIncident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AttendanceIncidentRepository extends JpaRepository<AttendanceIncident, UUID> {

    Page<AttendanceIncident> findByTenantIdAndStaffIdOrderByCreatedAtDesc(UUID tenantId, UUID staffId, Pageable pageable);

    List<AttendanceIncident> findByTenantIdAndStaffIdAndWorkDateBetweenOrderByCreatedAtDesc(
            UUID tenantId, UUID staffId, java.time.LocalDate start, java.time.LocalDate end);
}
