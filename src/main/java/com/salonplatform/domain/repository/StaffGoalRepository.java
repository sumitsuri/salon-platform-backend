package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.StaffGoal;
import com.salonplatform.domain.enums.StaffGoalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StaffGoalRepository extends JpaRepository<StaffGoal, UUID> {
    List<StaffGoal> findByTenantIdAndStaffIdOrderByPeriodEndDescCreatedAtDesc(UUID tenantId, UUID staffId);

    List<StaffGoal> findByTenantIdAndStaffIdAndStatusOrderByPeriodEndDesc(
            UUID tenantId, UUID staffId, StaffGoalStatus status);
}
