package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByBookingId(UUID bookingId);
    List<Payment> findByTenantIdAndBranchId(UUID tenantId, UUID branchId);
}
