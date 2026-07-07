package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.PaymentSplit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentSplitRepository extends JpaRepository<PaymentSplit, UUID> {
    List<PaymentSplit> findByPaymentId(UUID paymentId);
}
