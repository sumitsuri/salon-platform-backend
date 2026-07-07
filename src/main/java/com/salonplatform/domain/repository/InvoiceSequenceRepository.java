package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.InvoiceSequence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceSequenceRepository extends JpaRepository<InvoiceSequence, UUID> {
    Optional<InvoiceSequence> findByBranchIdAndFiscalYear(UUID branchId, String fiscalYear);
    List<InvoiceSequence> findByBranchId(UUID branchId);
}
