package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.InventoryProduct;
import com.salonplatform.domain.enums.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryProductRepository extends JpaRepository<InventoryProduct, UUID> {
    List<InventoryProduct> findByTenantIdAndActiveTrueOrderByNameAsc(UUID tenantId);
    List<InventoryProduct> findByTenantIdAndCategoryAndActiveTrueOrderByNameAsc(UUID tenantId, ProductCategory category);
    Optional<InventoryProduct> findByTenantIdAndSku(UUID tenantId, String sku);
}
