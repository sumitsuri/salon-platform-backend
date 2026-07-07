package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.Booking;
import com.salonplatform.domain.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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
}
