package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorRepository extends JpaRepository<Vendor, UUID> {
    List<Vendor> findByTenantIdAndActiveTrueOrderByNameAsc(UUID tenantId);
    Optional<Vendor> findByTenantIdAndNameIgnoreCase(UUID tenantId, String name);
}
