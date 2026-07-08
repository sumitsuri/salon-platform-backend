package com.salonplatform.service;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.BranchExpenditure;
import com.salonplatform.domain.entity.Staff;
import com.salonplatform.domain.enums.ExpenditureCategory;
import com.salonplatform.domain.repository.BranchExpenditureRepository;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.StaffRepository;
import com.salonplatform.dto.expenditure.CreateExpenditureRequest;
import com.salonplatform.dto.expenditure.ExpenditureResponse;
import com.salonplatform.dto.expenditure.UpdateExpenditureRequest;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenditureService {

    private final BranchExpenditureRepository expenditureRepository;
    private final BranchRepository branchRepository;
    private final StaffRepository staffRepository;

    public List<ExpenditureResponse> list(UUID branchId, LocalDate fromMonth, LocalDate toMonth) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();

        List<BranchExpenditure> items;
        if (fromMonth != null && toMonth != null) {
            items = expenditureRepository.findByTenantIdAndExpenseMonthBetweenAndActiveTrue(
                    tenantId, normalizeMonth(fromMonth), normalizeMonth(toMonth));
        } else if (branchId != null) {
            items = expenditureRepository.findByTenantIdAndBranchIdAndActiveTrueOrderByExpenseMonthDesc(
                    tenantId, branchId);
        } else {
            items = expenditureRepository.findByTenantIdAndActiveTrueOrderByExpenseMonthDesc(tenantId);
        }

        if (branchId != null && fromMonth != null) {
            UUID bid = branchId;
            items = items.stream().filter(e -> e.getBranchId().equals(bid)).collect(Collectors.toList());
        }

        return items.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public ExpenditureResponse get(UUID id) {
        SecurityUtils.assertBrandAdminOrAbove();
        return toResponse(requireExpenditure(SecurityUtils.requireTenantId(), id));
    }

    @Transactional
    public ExpenditureResponse create(CreateExpenditureRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        requireBranch(tenantId, request.getBranchId());

        LocalDate month = normalizeMonth(request.getExpenseMonth());
        if (request.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Amount must be non-negative");
        }

        BranchExpenditure expenditure = expenditureRepository.save(BranchExpenditure.builder()
                .tenantId(tenantId)
                .branchId(request.getBranchId())
                .category(request.getCategory())
                .expenseMonth(month)
                .amount(request.getAmount())
                .description(request.getDescription())
                .active(true)
                .build());
        return toResponse(expenditure);
    }

    @Transactional
    public ExpenditureResponse update(UUID id, UpdateExpenditureRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        BranchExpenditure expenditure = requireExpenditure(tenantId, id);

        if (request.getBranchId() != null) {
            requireBranch(tenantId, request.getBranchId());
            expenditure.setBranchId(request.getBranchId());
        }
        if (request.getCategory() != null) expenditure.setCategory(request.getCategory());
        if (request.getExpenseMonth() != null) expenditure.setExpenseMonth(normalizeMonth(request.getExpenseMonth()));
        if (request.getAmount() != null) {
            if (request.getAmount().compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestException("Amount must be non-negative");
            }
            expenditure.setAmount(request.getAmount());
        }
        if (request.getDescription() != null) expenditure.setDescription(request.getDescription());

        return toResponse(expenditureRepository.save(expenditure));
    }

    @Transactional
    public void deactivate(UUID id) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        BranchExpenditure expenditure = requireExpenditure(tenantId, id);
        expenditure.setActive(false);
        expenditureRepository.save(expenditure);
    }

    /** Upsert EMPLOYEE_SALARY line items from active staff payroll per branch for a month. */
    @Transactional
    public List<ExpenditureResponse> syncPayroll(LocalDate expenseMonth) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        LocalDate month = normalizeMonth(expenseMonth);

        List<Branch> branches = branchRepository.findByTenantId(tenantId);
        List<Staff> staffList = staffRepository.findByTenantId(tenantId).stream()
                .filter(Staff::isActive)
                .filter(s -> s.getSalary() != null && s.getSalary().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());

        return branches.stream().map(branch -> {
            BigDecimal payroll = staffList.stream()
                    .filter(s -> s.getBranchId().equals(branch.getId()))
                    .map(Staff::getSalary)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (payroll.compareTo(BigDecimal.ZERO) == 0) {
                return null;
            }

            BranchExpenditure existing = expenditureRepository
                    .findByTenantIdAndBranchIdAndCategoryAndExpenseMonthAndActiveTrue(
                            tenantId, branch.getId(), ExpenditureCategory.EMPLOYEE_SALARY, month)
                    .orElse(null);

            BranchExpenditure saved;
            if (existing != null) {
                existing.setAmount(payroll);
                existing.setDescription("Auto-synced from active staff salaries");
                saved = expenditureRepository.save(existing);
            } else {
                saved = expenditureRepository.save(BranchExpenditure.builder()
                        .tenantId(tenantId)
                        .branchId(branch.getId())
                        .category(ExpenditureCategory.EMPLOYEE_SALARY)
                        .expenseMonth(month)
                        .amount(payroll)
                        .description("Auto-synced from active staff salaries")
                        .active(true)
                        .build());
            }
            return toResponse(saved);
        }).filter(r -> r != null).collect(Collectors.toList());
    }

    private BranchExpenditure requireExpenditure(UUID tenantId, UUID id) {
        BranchExpenditure expenditure = expenditureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expenditure not found"));
        if (!expenditure.getTenantId().equals(tenantId) || !expenditure.isActive()) {
            throw new ResourceNotFoundException("Expenditure not found");
        }
        return expenditure;
    }

    private Branch requireBranch(UUID tenantId, UUID branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
        if (!branch.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Branch not found");
        }
        return branch;
    }

    private LocalDate normalizeMonth(LocalDate date) {
        return date.withDayOfMonth(1);
    }

    private ExpenditureResponse toResponse(BranchExpenditure e) {
        String branchName = branchRepository.findById(e.getBranchId()).map(Branch::getName).orElse("—");
        return ExpenditureResponse.builder()
                .id(e.getId())
                .branchId(e.getBranchId())
                .branchName(branchName)
                .category(e.getCategory())
                .expenseMonth(e.getExpenseMonth())
                .amount(e.getAmount())
                .description(e.getDescription())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
