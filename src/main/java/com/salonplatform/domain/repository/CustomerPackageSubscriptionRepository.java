package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.CustomerPackageSubscription;
import com.salonplatform.domain.enums.PackageSubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CustomerPackageSubscriptionRepository extends JpaRepository<CustomerPackageSubscription, UUID> {

    List<CustomerPackageSubscription> findByTenantIdAndCustomerIdAndStatusOrderByExpiresOnDesc(
            UUID tenantId, UUID customerId, PackageSubscriptionStatus status);

    List<CustomerPackageSubscription> findByTenantIdAndCustomerIdAndStatusAndExpiresOnGreaterThanEqualOrderByExpiresOnAsc(
            UUID tenantId, UUID customerId, PackageSubscriptionStatus status, LocalDate today);

    @Query("""
            SELECT s FROM CustomerPackageSubscription s
            WHERE s.tenantId = :tenantId AND s.branchId = :branchId
              AND s.status = :status AND s.expiresOn >= :today
            ORDER BY s.expiresOn ASC
            """)
    List<CustomerPackageSubscription> findActiveForBranch(
            @Param("tenantId") UUID tenantId,
            @Param("branchId") UUID branchId,
            @Param("status") PackageSubscriptionStatus status,
            @Param("today") LocalDate today);

    @Query("""
            SELECT s FROM CustomerPackageSubscription s
            WHERE s.tenantId = :tenantId AND s.branchId = :branchId
              AND s.status = :status AND s.expiresOn BETWEEN :from AND :to
            ORDER BY s.expiresOn ASC
            """)
    List<CustomerPackageSubscription> findExpiringForBranch(
            @Param("tenantId") UUID tenantId,
            @Param("branchId") UUID branchId,
            @Param("status") PackageSubscriptionStatus status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            SELECT s FROM CustomerPackageSubscription s
            WHERE s.tenantId = :tenantId
              AND s.status = :status AND s.expiresOn BETWEEN :from AND :to
            ORDER BY s.expiresOn ASC
            """)
    List<CustomerPackageSubscription> findExpiringForTenant(
            @Param("tenantId") UUID tenantId,
            @Param("status") PackageSubscriptionStatus status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            SELECT s FROM CustomerPackageSubscription s
            WHERE s.tenantId = :tenantId AND s.status = :status AND s.expiresOn < :today
            """)
    List<CustomerPackageSubscription> findExpiredActive(
            @Param("tenantId") UUID tenantId,
            @Param("status") PackageSubscriptionStatus status,
            @Param("today") LocalDate today);
}
