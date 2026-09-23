package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByBookingId(UUID bookingId);
    List<Payment> findByTenantIdAndBranchId(UUID tenantId, UUID branchId);
    List<Payment> findByTenantId(UUID tenantId);

    /** Half-open range [start, end) — matches the in-Java filtering this replaces. */
    @Query("SELECT p FROM Payment p WHERE p.tenantId = :tenantId AND p.paidAt >= :start AND p.paidAt < :end")
    List<Payment> findByTenantIdAndPaidAtRange(
            @Param("tenantId") UUID tenantId, @Param("start") Instant start, @Param("end") Instant end);
}
