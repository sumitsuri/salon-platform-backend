package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.BranchExpenditure;
import com.salonplatform.domain.enums.ExpenditureCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchExpenditureRepository extends JpaRepository<BranchExpenditure, UUID> {
    List<BranchExpenditure> findByTenantIdAndActiveTrueOrderByExpenseMonthDesc(UUID tenantId);

    List<BranchExpenditure> findByTenantIdAndBranchIdAndActiveTrueOrderByExpenseMonthDesc(
            UUID tenantId, UUID branchId);

    List<BranchExpenditure> findByTenantIdAndExpenseMonthBetweenAndActiveTrue(
            UUID tenantId, LocalDate fromMonth, LocalDate toMonth);

    Optional<BranchExpenditure> findByTenantIdAndBranchIdAndCategoryAndExpenseMonthAndActiveTrue(
            UUID tenantId, UUID branchId, ExpenditureCategory category, LocalDate expenseMonth);
}
