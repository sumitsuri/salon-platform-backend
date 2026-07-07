package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    Optional<Invoice> findByBookingId(UUID bookingId);
    List<Invoice> findByTenantIdOrderByIssuedAtDesc(UUID tenantId);

    @Query("SELECT i FROM Invoice i WHERE i.tenantId = :tenantId AND i.issuedAt >= :start AND i.issuedAt < :end")
    List<Invoice> findByTenantAndDateRange(@Param("tenantId") UUID tenantId,
                                           @Param("start") Instant start,
                                           @Param("end") Instant end);

    @Query("SELECT i FROM Invoice i WHERE i.tenantId = :tenantId AND i.branchId = :branchId " +
           "AND i.issuedAt >= :start AND i.issuedAt < :end")
    List<Invoice> findByBranchAndDateRange(@Param("tenantId") UUID tenantId,
                                           @Param("branchId") UUID branchId,
                                           @Param("start") Instant start,
                                           @Param("end") Instant end);
}
