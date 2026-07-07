package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.Tenant;
import com.salonplatform.domain.enums.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findBySlug(String slug);
    List<Tenant> findByStatus(TenantStatus status);
}
