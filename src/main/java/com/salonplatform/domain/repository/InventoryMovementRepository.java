package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.InventoryMovement;
import com.salonplatform.domain.enums.MovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {
    List<InventoryMovement> findByTenantIdAndBranchIdAndMovementDateBetweenOrderByMovementDateDescCreatedAtDesc(
            UUID tenantId, UUID branchId, LocalDate from, LocalDate to);

    List<InventoryMovement> findByTenantIdAndMovementDateBetweenOrderByMovementDateDescCreatedAtDesc(
            UUID tenantId, LocalDate from, LocalDate to);

    List<InventoryMovement> findByTenantIdAndBranchIdInAndMovementDateBetweenOrderByMovementDateDescCreatedAtDesc(
            UUID tenantId, List<UUID> branchIds, LocalDate from, LocalDate to);

    @Query("""
            SELECT COALESCE(SUM(m.totalCost), 0) FROM InventoryMovement m
            WHERE m.tenantId = :tenantId AND m.branchId = :branchId
            AND m.movementDate >= :monthStart AND m.movementDate <= :monthEnd
            AND m.movementType IN :types
            """)
    BigDecimal sumCostByBranchAndMonth(
            @Param("tenantId") UUID tenantId,
            @Param("branchId") UUID branchId,
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd,
            @Param("types") List<MovementType> types);
}
