package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.SalonService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SalonServiceRepository extends JpaRepository<SalonService, UUID> {
    List<SalonService> findByTenantIdAndActiveTrue(UUID tenantId);
    List<SalonService> findByTenantIdAndCategoryIdAndActiveTrue(UUID tenantId, UUID categoryId);
    List<SalonService> findByTenantId(UUID tenantId);
}
