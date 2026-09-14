package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.ServicePackagePlanItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ServicePackagePlanItemRepository extends JpaRepository<ServicePackagePlanItem, UUID> {

    List<ServicePackagePlanItem> findByPlanIdOrderBySortOrderAsc(UUID planId);

    void deleteByPlanId(UUID planId);
}
