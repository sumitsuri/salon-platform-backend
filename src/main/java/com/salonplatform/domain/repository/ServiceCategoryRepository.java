package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.ServiceCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, UUID> {
    List<ServiceCategory> findByTenantIdAndActiveTrueOrderBySortOrderAsc(UUID tenantId);
    List<ServiceCategory> findByTenantId(UUID tenantId);
}
