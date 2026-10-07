package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.StaffPerformanceReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StaffPerformanceReviewRepository extends JpaRepository<StaffPerformanceReview, UUID> {
    List<StaffPerformanceReview> findByTenantIdAndStaffIdAndVisibleToStaffTrueOrderByReviewDateDescCreatedAtDesc(
            UUID tenantId, UUID staffId);

    List<StaffPerformanceReview> findByTenantIdAndStaffIdOrderByReviewDateDescCreatedAtDesc(
            UUID tenantId, UUID staffId);
}
