package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.Booking;
import com.salonplatform.domain.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {
    List<Booking> findByTenantIdAndBranchIdOrderByCreatedAtDesc(UUID tenantId, UUID branchId);
    List<Booking> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    @Query("SELECT b FROM Booking b WHERE b.tenantId = :tenantId AND b.branchId = :branchId " +
           "AND b.createdAt >= :start AND b.createdAt < :end ORDER BY b.createdAt DESC")
    List<Booking> findByBranchAndDateRange(@Param("tenantId") UUID tenantId,
                                           @Param("branchId") UUID branchId,
                                           @Param("start") Instant start,
                                           @Param("end") Instant end);

    List<Booking> findByTenantIdAndBranchIdAndStatus(UUID tenantId, UUID branchId, BookingStatus status);

    @Query("""
            SELECT b FROM Booking b
            WHERE b.tenantId = :tenantId AND b.branchId = :branchId
              AND b.status = com.salonplatform.domain.enums.BookingStatus.CONFIRMED
              AND b.scheduledStartAt >= :start AND b.scheduledStartAt < :end
            """)
    List<Booking> findConfirmedScheduledBetween(@Param("tenantId") UUID tenantId,
                                              @Param("branchId") UUID branchId,
                                              @Param("start") Instant start,
                                              @Param("end") Instant end);

    @Query("""
            SELECT b.customerId AS customerId,
                   COUNT(b) AS visitCount,
                   MAX(b.createdAt) AS lastVisitAt,
                   COALESCE(SUM(i.grandTotal), 0) AS lifetimeSpend
            FROM Booking b
            LEFT JOIN Invoice i ON i.bookingId = b.id
            WHERE b.customerId IN :customerIds
              AND b.branchId = :branchId
              AND b.status = com.salonplatform.domain.enums.BookingStatus.COMPLETED
            GROUP BY b.customerId
            """)
    List<CustomerBranchStatsRow> aggregateBranchStatsForCustomers(
            @Param("customerIds") Collection<UUID> customerIds,
            @Param("branchId") UUID branchId);

    @Query(
            value = """
                    SELECT DISTINCT ON (b.customer_id)
                           b.customer_id AS customerId,
                           br.name AS branchName
                    FROM bookings b
                    JOIN branches br ON br.id = b.branch_id
                    WHERE b.customer_id IN (:customerIds)
                      AND b.status = 'COMPLETED'
                    ORDER BY b.customer_id, COALESCE(b.completed_at, b.created_at) DESC
                    """,
            nativeQuery = true)
    List<CustomerLastVisitBranchRow> findLastVisitBranchNamesForCustomers(
            @Param("customerIds") Collection<UUID> customerIds);
}
