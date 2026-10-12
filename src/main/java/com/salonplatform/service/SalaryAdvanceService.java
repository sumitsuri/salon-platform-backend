package com.salonplatform.service;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.LeaveRecord;
import com.salonplatform.domain.entity.SalaryAdvance;
import com.salonplatform.domain.entity.SalaryAdvanceInstallment;
import com.salonplatform.domain.entity.SalaryAdvancePolicy;
import com.salonplatform.domain.entity.Staff;
import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.enums.AdvanceStatus;
import com.salonplatform.domain.enums.InstallmentStatus;
import com.salonplatform.domain.enums.LeaveStatus;
import com.salonplatform.domain.enums.LeaveType;
import com.salonplatform.domain.repository.AttendanceRecordRepository;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.LeaveRecordRepository;
import com.salonplatform.domain.repository.SalaryAdvanceInstallmentRepository;
import com.salonplatform.domain.repository.SalaryAdvancePolicyRepository;
import com.salonplatform.domain.repository.SalaryAdvanceRepository;
import com.salonplatform.domain.repository.StaffRepository;
import com.salonplatform.domain.repository.UserRepository;
import com.salonplatform.dto.advance.*;
import com.salonplatform.dto.common.PageResponse;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.util.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Salary advances: employees ask from the staff app, the brand owner decides against a shared budget, and approved
 * money is recovered in monthly installments. The budget is a revolving pool — money out is the unrecovered
 * installments, so repayments free it up again — and per-employee limits stop a few people using all of it.
 */
@Service
@RequiredArgsConstructor
public class SalaryAdvanceService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final SalaryAdvanceRepository advanceRepository;
    private final SalaryAdvanceInstallmentRepository installmentRepository;
    private final SalaryAdvancePolicyRepository policyRepository;
    private final StaffRepository staffRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final LeaveRecordRepository leaveRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final StaffAccessService staffAccessService;
    private final AuditService auditService;

    // ------------------------------------------------------------------ employee

    @Transactional(readOnly = true)
    public MyAdvancesResponse myAdvances() {
        Staff staff = staffAccessService.requireCurrentStaff();
        SalaryAdvancePolicy policy = policyRepository.findByTenantId(staff.getTenantId()).orElse(null);
        List<SalaryAdvance> mine = advanceRepository.findByStaffIdOrderByCreatedAtDesc(staff.getId());
        BigDecimal outstanding = installmentRepository.sumOutstandingForStaff(staff.getId());

        List<AdvanceResponse> responses = toResponses(mine, false);
        BigDecimal totalRequested = mine.stream()
                .map(SalaryAdvance::getRequestedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalApproved = mine.stream()
                .filter(a -> a.getApprovedAmount() != null)
                .map(SalaryAdvance::getApprovedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        AdvanceInstallmentResponse next = responses.stream()
                .filter(r -> r.getStatus() == AdvanceStatus.APPROVED)
                .flatMap(r -> r.getInstallments().stream())
                .filter(i -> i.getStatus() == InstallmentStatus.SCHEDULED)
                .min(Comparator.comparing(AdvanceInstallmentResponse::getDueMonth))
                .orElse(null);

        return MyAdvancesResponse.builder()
                .eligibility(eligibility(staff, policy, mine))
                .advances(responses)
                .requestCount(mine.size())
                .totalRequested(SalaryAdvanceRules.money(totalRequested))
                .totalApproved(SalaryAdvanceRules.money(totalApproved))
                .outstanding(SalaryAdvanceRules.money(outstanding))
                .nextInstallment(next)
                .build();
    }

    @Transactional
    public AdvanceResponse request(CreateAdvanceRequest request) {
        Staff staff = staffAccessService.requireCurrentStaff();
        SalaryAdvancePolicy policy = policyRepository.findByTenantId(staff.getTenantId()).orElse(null);
        List<SalaryAdvance> mine = advanceRepository.findByStaffIdOrderByCreatedAtDesc(staff.getId());
        AdvanceEligibilityResponse eligible = eligibility(staff, policy, mine);
        if (!eligible.isCanRequest()) {
            throw new BadRequestException(blockedMessage(eligible));
        }
        BigDecimal amount = SalaryAdvanceRules.money(request.getAmount());
        if (amount.compareTo(eligible.getMaxAmount()) > 0) {
            throw new BadRequestException("You can request up to " + rupees(eligible.getMaxAmount()) + " right now.");
        }
        if (request.getInstallments() > policy.getMaxInstallments()) {
            throw new BadRequestException("Repayment can be spread over at most " + policy.getMaxInstallments() + " months.");
        }

        SalaryAdvance saved = advanceRepository.save(SalaryAdvance.builder()
                .tenantId(staff.getTenantId())
                .branchId(staff.getBranchId())
                .staffId(staff.getId())
                .requestedAmount(amount)
                .requestedInstallments(request.getInstallments())
                .reason(request.getReason().trim())
                .status(AdvanceStatus.PENDING)
                .createdByUserId(SecurityUtils.currentUserId())
                .build());
        auditService.log("ADVANCE_REQUESTED", "SalaryAdvance", saved.getId(),
                rupees(amount) + " over " + request.getInstallments() + " months");
        return toResponses(List.of(saved), false).get(0);
    }

    @Transactional
    public AdvanceResponse cancel(UUID advanceId) {
        Staff staff = staffAccessService.requireCurrentStaff();
        SalaryAdvance advance = requireAdvance(staff.getTenantId(), advanceId);
        if (!advance.getStaffId().equals(staff.getId())) {
            throw new com.salonplatform.exception.ForbiddenException("You can only cancel your own request");
        }
        if (advance.getStatus() != AdvanceStatus.PENDING) {
            throw new BadRequestException("Only a request that is still waiting for a decision can be cancelled.");
        }
        advance.setStatus(AdvanceStatus.CANCELLED);
        advanceRepository.save(advance);
        auditService.log("ADVANCE_CANCELLED", "SalaryAdvance", advance.getId(), null);
        return toResponses(List.of(advance), false).get(0);
    }

    // ------------------------------------------------------------------ owner: budget and policy

    @Transactional(readOnly = true)
    public AdvanceBudgetResponse budget() {
        SecurityUtils.assertBrandAdmin();
        UUID tenantId = SecurityUtils.requireTenantId();
        SalaryAdvancePolicy policy = policyRepository.findByTenantId(tenantId).orElse(null);
        BigDecimal pool = policy != null ? SalaryAdvanceRules.money(policy.getPoolAmount()) : BigDecimal.ZERO.setScale(2);
        BigDecimal outstanding = SalaryAdvanceRules.money(installmentRepository.sumOutstanding(tenantId));
        LocalDate month = SalaryAdvanceRules.startOfMonth(LocalDate.now(ZONE));
        BigDecimal dueThisMonth = installmentRepository.findByTenantIdAndDueMonthAndStatus(tenantId, month, InstallmentStatus.SCHEDULED)
                .stream().map(SalaryAdvanceInstallment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        int pendingCount = advanceRepository.findByTenantIdAndStatus(tenantId, AdvanceStatus.PENDING).size();

        return AdvanceBudgetResponse.builder()
                .policy(policyResponse(policy))
                .poolAmount(pool)
                .outstanding(outstanding)
                .available(SalaryAdvanceRules.poolAvailable(pool, outstanding))
                .pendingRequested(SalaryAdvanceRules.money(advanceRepository.sumPendingRequested(tenantId)))
                .pendingCount(pendingCount)
                .utilizationPct(SalaryAdvanceRules.percentOf(outstanding, pool))
                .perEmployeeCap(policy != null ? SalaryAdvanceRules.perEmployeeCap(pool, policy.getMaxPoolSharePercent()) : BigDecimal.ZERO.setScale(2))
                .dueThisMonth(SalaryAdvanceRules.money(dueThisMonth))
                .build();
    }

    @Transactional
    public AdvancePolicyResponse updatePolicy(AdvancePolicyRequest request) {
        SecurityUtils.assertBrandAdmin();
        UUID tenantId = SecurityUtils.requireTenantId();
        SalaryAdvancePolicy policy = policyRepository.findByTenantIdForUpdate(tenantId)
                .orElseGet(() -> SalaryAdvancePolicy.builder().tenantId(tenantId).build());
        policy.setPoolAmount(SalaryAdvanceRules.money(request.getPoolAmount()));
        policy.setMaxPoolSharePercent(request.getMaxPoolSharePercent());
        policy.setMaxSalaryMultiple(request.getMaxSalaryMultiple().setScale(2, java.math.RoundingMode.HALF_UP));
        policy.setMaxInstallments(request.getMaxInstallments());
        policy.setCooldownDays(request.getCooldownDays());
        policy.setEnabled(request.isEnabled());
        policy.setUpdatedByUserId(SecurityUtils.currentUserId());
        SalaryAdvancePolicy saved = policyRepository.save(policy);
        auditService.log("ADVANCE_POLICY_UPDATED", "SalaryAdvancePolicy", saved.getId(),
                "pool " + rupees(saved.getPoolAmount()) + ", share " + saved.getMaxPoolSharePercent() + "%");
        return policyResponse(saved);
    }

    // ------------------------------------------------------------------ owner: requests

    @Transactional(readOnly = true)
    public PageResponse<AdvanceResponse> list(
            AdvanceStatus status, UUID staffId, UUID branchId, Boolean excludePending, Integer page, Integer size) {
        SecurityUtils.assertBrandAdmin();
        UUID tenantId = SecurityUtils.requireTenantId();
        Page<SalaryAdvance> result = advanceRepository.search(
                tenantId, status, Boolean.TRUE.equals(excludePending), staffId, branchId,
                PageRequest.of(PageUtils.normalizePage(page != null ? page : 0), PageUtils.normalizeSize(size != null ? size : 20),
                        Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.<AdvanceResponse>builder()
                .content(toResponses(result.getContent(), true))
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional
    public AdvanceResponse grant(GrantAdvanceRequest request) {
        SecurityUtils.assertBrandAdmin();
        UUID tenantId = SecurityUtils.requireTenantId();
        SalaryAdvancePolicy policy = requireConfiguredPolicyForUpdate(tenantId);
        Staff staff = staffRepository.findById(request.getStaffId())
                .filter(s -> s.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));

        BigDecimal amount = SalaryAdvanceRules.money(request.getAmount());
        int installments = request.getInstallments();
        assertInstallmentCount(installments, policy);
        LocalDate firstMonth = resolveFirstDeductionMonth(request.getFirstDeductionMonth());
        assertGrantWithinBudget(staff, amount, policy, tenantId);

        String reason = request.getReason() != null && !request.getReason().isBlank()
                ? request.getReason().trim()
                : "Salary advance recorded by your manager.";

        SalaryAdvance advance = advanceRepository.save(SalaryAdvance.builder()
                .tenantId(tenantId)
                .branchId(staff.getBranchId())
                .staffId(staff.getId())
                .requestedAmount(amount)
                .requestedInstallments(installments)
                .reason(reason)
                .status(AdvanceStatus.APPROVED)
                .approvedAmount(amount)
                .approvedInstallments(installments)
                .firstDeductionMonth(firstMonth)
                .createdByUserId(SecurityUtils.currentUserId())
                .build());
        stampDecision(advance, request.getNote());
        advanceRepository.save(advance);
        createScheduledInstallments(advance.getId(), tenantId, staff.getId(), amount, installments, firstMonth);
        auditService.log("ADVANCE_GRANTED", "SalaryAdvance", advance.getId(),
                staff.getName() + ": " + rupees(amount) + " over " + installments + " months (owner recorded)");
        return toResponses(List.of(advance), true).get(0);
    }

    @Transactional
    public AdvanceResponse approve(UUID advanceId, DecideAdvanceRequest request) {
        SecurityUtils.assertBrandAdmin();
        UUID tenantId = SecurityUtils.requireTenantId();
        SalaryAdvancePolicy policy = requireConfiguredPolicyForUpdate(tenantId);
        SalaryAdvance advance = requireAdvance(tenantId, advanceId);
        if (advance.getStatus() != AdvanceStatus.PENDING) {
            throw new BadRequestException("This request has already been decided.");
        }

        BigDecimal amount = SalaryAdvanceRules.money(
                request.getApprovedAmount() != null ? request.getApprovedAmount() : advance.getRequestedAmount());
        if (amount.compareTo(advance.getRequestedAmount()) > 0) {
            throw new BadRequestException("You cannot approve more than the " + rupees(advance.getRequestedAmount()) + " requested.");
        }
        int installments = request.getInstallments() != null ? request.getInstallments() : advance.getRequestedInstallments();
        assertInstallmentCount(installments, policy);

        Staff staff = staffRepository.findById(advance.getStaffId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));
        assertGrantWithinBudget(staff, amount, policy, tenantId);
        LocalDate firstMonth = resolveFirstDeductionMonth(request.getFirstDeductionMonth());

        createScheduledInstallments(advance.getId(), tenantId, staff.getId(), amount, installments, firstMonth);
        advance.setStatus(AdvanceStatus.APPROVED);
        advance.setApprovedAmount(amount);
        advance.setApprovedInstallments(installments);
        advance.setFirstDeductionMonth(firstMonth);
        stampDecision(advance, request.getNote());
        advanceRepository.save(advance);
        auditService.log("ADVANCE_APPROVED", "SalaryAdvance", advance.getId(),
                staff.getName() + ": " + rupees(amount) + " over " + installments + " months");
        return toResponses(List.of(advance), true).get(0);
    }

    @Transactional
    public AdvanceResponse reject(UUID advanceId, DecideAdvanceRequest request) {
        SecurityUtils.assertBrandAdmin();
        UUID tenantId = SecurityUtils.requireTenantId();
        SalaryAdvance advance = requireAdvance(tenantId, advanceId);
        if (advance.getStatus() != AdvanceStatus.PENDING) {
            throw new BadRequestException("This request has already been decided.");
        }
        advance.setStatus(AdvanceStatus.REJECTED);
        stampDecision(advance, request != null ? request.getNote() : null);
        advanceRepository.save(advance);
        auditService.log("ADVANCE_REJECTED", "SalaryAdvance", advance.getId(), null);
        return toResponses(List.of(advance), true).get(0);
    }

    @Transactional
    public AdvanceResponse update(UUID advanceId, UpdateAdvanceRequest request) {
        SecurityUtils.assertBrandAdmin();
        UUID tenantId = SecurityUtils.requireTenantId();
        SalaryAdvance advance = requireAdvance(tenantId, advanceId);
        if (advance.getStatus() == AdvanceStatus.PENDING) {
            throw new BadRequestException("Pending requests must be approved or rejected from the queue.");
        }

        if (request.getReason() != null && !request.getReason().isBlank()) {
            advance.setReason(request.getReason().trim());
        }
        if (request.getNote() != null) {
            advance.setDecisionNote(request.getNote().isBlank() ? null : request.getNote().trim());
            advance.setDecidedByUserId(SecurityUtils.currentUserId());
            advance.setDecidedAt(Instant.now());
        }

        if (Boolean.TRUE.equals(request.getRevoke())) {
            if (advance.getStatus() != AdvanceStatus.APPROVED && advance.getStatus() != AdvanceStatus.COMPLETED) {
                throw new BadRequestException("Only an active advance can be revoked.");
            }
            for (SalaryAdvanceInstallment i : scheduledInstallments(advance.getId())) {
                settle(i, InstallmentStatus.WAIVED, "Revoked by owner");
            }
            advance.setStatus(AdvanceStatus.CANCELLED);
            advanceRepository.save(advance);
            auditService.log("ADVANCE_REVOKED", "SalaryAdvance", advance.getId(), null);
            return toResponses(List.of(advance), true).get(0);
        }

        if (request.getApprovedAmount() != null || request.getInstallments() != null || request.getFirstDeductionMonth() != null) {
            if (advance.getStatus() != AdvanceStatus.APPROVED && advance.getStatus() != AdvanceStatus.COMPLETED) {
                throw new BadRequestException("Only approved advances can be rescheduled.");
            }
            rescheduleApprovedAdvance(advance, request, tenantId);
        }

        advanceRepository.save(advance);
        auditService.log("ADVANCE_UPDATED", "SalaryAdvance", advance.getId(), null);
        return toResponses(List.of(advance), true).get(0);
    }

    // ------------------------------------------------------------------ owner: per-employee history

    @Transactional(readOnly = true)
    public PageResponse<EmployeeAdvanceSummary> employeeSummaries(
            UUID branchId, Boolean onlyAsked, String search, Integer page, Integer size) {
        SecurityUtils.assertBrandAdmin();
        UUID tenantId = SecurityUtils.requireTenantId();
        SalaryAdvancePolicy policy = policyRepository.findByTenantId(tenantId).orElse(null);
        Map<UUID, List<SalaryAdvance>> byStaff = advanceRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.groupingBy(SalaryAdvance::getStaffId));
        List<SalaryAdvanceInstallment> scheduled = installmentRepository.findScheduledByTenant(tenantId);
        Map<UUID, BigDecimal> outstandingByStaff = scheduled.stream().collect(
                Collectors.groupingBy(SalaryAdvanceInstallment::getStaffId,
                        Collectors.reducing(BigDecimal.ZERO, SalaryAdvanceInstallment::getAmount, BigDecimal::add)));
        BigDecimal outstandingTotal = outstandingByStaff.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<UUID, String> branchNames = branchNames(tenantId);
        String q = search != null ? search.trim().toLowerCase() : "";

        List<EmployeeAdvanceSummary> out = new ArrayList<>();
        for (Staff s : staffRepository.findByTenantId(tenantId)) {
            List<SalaryAdvance> theirs = byStaff.getOrDefault(s.getId(), List.of());
            if (!s.isActive() && theirs.isEmpty()) {
                continue;
            }
            if (branchId != null && !branchId.equals(s.getBranchId())) {
                continue;
            }
            EmployeeAdvanceSummary row = summarize(s, theirs, outstandingByStaff.getOrDefault(s.getId(), BigDecimal.ZERO),
                    outstandingTotal, policy, branchNames.get(s.getBranchId()));
            if (Boolean.TRUE.equals(onlyAsked) && row.getRequestCount() == 0) {
                continue;
            }
            if (!q.isEmpty() && !row.getStaffName().toLowerCase().contains(q)) {
                continue;
            }
            out.add(row);
        }
        out.sort(Comparator.comparing(EmployeeAdvanceSummary::getOutstanding).reversed()
                .thenComparing(Comparator.comparingInt(EmployeeAdvanceSummary::getRequestCount).reversed())
                .thenComparing(EmployeeAdvanceSummary::getStaffName, String.CASE_INSENSITIVE_ORDER));
        return PageUtils.slice(out, page != null ? page : 0, size != null ? size : PageUtils.DEFAULT_PAGE_SIZE);
    }

    // ------------------------------------------------------------------ owner: deductions

    @Transactional(readOnly = true)
    public DeductionsResponse deductions(LocalDate month) {
        SecurityUtils.assertBrandAdmin();
        UUID tenantId = SecurityUtils.requireTenantId();
        return buildDeductions(tenantId, SalaryAdvanceRules.startOfMonth(month != null ? month : LocalDate.now(ZONE)));
    }

    @Transactional
    public AdvanceInstallmentResponse settleInstallment(UUID installmentId, InstallmentStatus target, String note) {
        SecurityUtils.assertBrandAdmin();
        if (target == InstallmentStatus.SCHEDULED) {
            throw new BadRequestException("Choose deducted or waived.");
        }
        UUID tenantId = SecurityUtils.requireTenantId();
        SalaryAdvanceInstallment installment = installmentRepository.findById(installmentId)
                .filter(i -> i.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Installment not found"));
        settle(installment, target, note);
        return toInstallment(installment);
    }

    @Transactional
    public DeductionsResponse deductMonth(LocalDate month) {
        SecurityUtils.assertBrandAdmin();
        UUID tenantId = SecurityUtils.requireTenantId();
        LocalDate start = SalaryAdvanceRules.startOfMonth(month != null ? month : LocalDate.now(ZONE));
        for (SalaryAdvanceInstallment i : installmentRepository.findByTenantIdAndDueMonthAndStatus(tenantId, start, InstallmentStatus.SCHEDULED)) {
            settle(i, InstallmentStatus.DEDUCTED, null);
        }
        return buildDeductions(tenantId, start);
    }

    private void settle(SalaryAdvanceInstallment installment, InstallmentStatus target, String note) {
        if (installment.getStatus() != InstallmentStatus.SCHEDULED) {
            throw new BadRequestException("This installment has already been settled.");
        }
        installment.setStatus(target);
        installment.setSettledAt(Instant.now());
        installment.setSettledByUserId(SecurityUtils.currentUserId());
        installment.setNote(note != null && !note.isBlank() ? note.trim() : null);
        installmentRepository.save(installment);
        auditService.log(target == InstallmentStatus.DEDUCTED ? "ADVANCE_INSTALLMENT_DEDUCTED" : "ADVANCE_INSTALLMENT_WAIVED",
                "SalaryAdvanceInstallment", installment.getId(), rupees(installment.getAmount()));

        if (installmentRepository.countByAdvanceIdAndStatus(installment.getAdvanceId(), InstallmentStatus.SCHEDULED) == 0) {
            advanceRepository.findById(installment.getAdvanceId()).ifPresent(a -> {
                if (a.getStatus() == AdvanceStatus.APPROVED) {
                    a.setStatus(AdvanceStatus.COMPLETED);
                    advanceRepository.save(a);
                }
            });
        }
    }

    private DeductionsResponse buildDeductions(UUID tenantId, LocalDate month) {
        List<SalaryAdvanceInstallment> all = installmentRepository.findByTenantIdAndDueMonth(tenantId, month);
        Map<UUID, List<SalaryAdvanceInstallment>> byStaff = all.stream()
                .collect(Collectors.groupingBy(SalaryAdvanceInstallment::getStaffId));
        Map<UUID, Staff> staffById = staffRepository.findAllById(byStaff.keySet()).stream()
                .collect(Collectors.toMap(Staff::getId, Function.identity()));
        Map<UUID, String> branchNames = branchNames(tenantId);
        LocalDate monthEnd = month.withDayOfMonth(month.lengthOfMonth());

        List<DeductionsResponse.Row> rows = new ArrayList<>();
        BigDecimal totalDue = BigDecimal.ZERO;
        BigDecimal totalDeducted = BigDecimal.ZERO;
        for (Map.Entry<UUID, List<SalaryAdvanceInstallment>> e : byStaff.entrySet()) {
            Staff s = staffById.get(e.getKey());
            if (s == null) {
                continue;
            }
            BigDecimal due = sum(e.getValue(), InstallmentStatus.SCHEDULED);
            BigDecimal deducted = sum(e.getValue(), InstallmentStatus.DEDUCTED);
            BigDecimal base = s.getSalary() != null ? SalaryAdvanceRules.money(s.getSalary()) : null;
            LocalDate today = LocalDate.now(ZONE);
            LocalDate proRateEnd = month.equals(SalaryAdvanceRules.startOfMonth(today)) && !today.isBefore(month)
                    ? today
                    : monthEnd;
            int present = (int) attendanceRepository
                    .findByStaffIdAndWorkDateBetweenOrderByWorkDateDesc(s.getId(), month, proRateEnd).stream()
                    .filter(r -> r.getEntryTime() != null)
                    .count();
            double leaveDays = approvedLeaveDays(s.getId(), month, proRateEnd);
            BigDecimal proRated = StaffSalaryProRating.proRated(base, present, leaveDays, proRateEnd);
            rows.add(DeductionsResponse.Row.builder()
                    .staffId(s.getId())
                    .staffName(s.getName())
                    .branchName(branchNames.get(s.getBranchId()))
                    .baseSalary(base)
                    .proRatedSalary(proRated)
                    .dueAmount(due)
                    .deductedAmount(deducted)
                    .netAfterAdvances(base != null ? base.subtract(due) : null)
                    .daysPresent(present)
                    .approvedLeaveDays(leaveDays)
                    .installments(e.getValue().stream().map(this::toInstallment).toList())
                    .build());
            totalDue = totalDue.add(due);
            totalDeducted = totalDeducted.add(deducted);
        }
        rows.sort(Comparator.comparing(DeductionsResponse.Row::getStaffName, String.CASE_INSENSITIVE_ORDER));
        return DeductionsResponse.builder()
                .month(month)
                .totalDue(SalaryAdvanceRules.money(totalDue))
                .totalDeducted(SalaryAdvanceRules.money(totalDeducted))
                .rows(rows)
                .build();
    }

    // ------------------------------------------------------------------ eligibility

    private AdvanceEligibilityResponse eligibility(Staff staff, SalaryAdvancePolicy policy, List<SalaryAdvance> mine) {
        LocalDate today = LocalDate.now(ZONE);
        int maxInstallments = policy != null ? policy.getMaxInstallments() : 6;
        AdvanceEligibilityResponse.AdvanceEligibilityResponseBuilder base = AdvanceEligibilityResponse.builder()
                .canRequest(false)
                .maxAmount(BigDecimal.ZERO.setScale(2))
                .maxInstallments(maxInstallments)
                .firstDeductionMonth(SalaryAdvanceRules.firstOfNextMonth(today));

        BigDecimal outstanding = SalaryAdvanceRules.money(installmentRepository.sumOutstandingForStaff(staff.getId()));
        base.outstanding(outstanding);

        if (policy == null || policy.getPoolAmount().signum() <= 0) {
            return base.blockedReason("NOT_CONFIGURED").build();
        }
        if (!policy.isEnabled()) {
            return base.blockedReason("DISABLED").build();
        }
        if (mine.stream().anyMatch(a -> a.getStatus() == AdvanceStatus.PENDING)) {
            return base.blockedReason("PENDING_REQUEST").build();
        }
        Instant lastGranted = mine.stream()
                .filter(a -> a.getStatus() == AdvanceStatus.APPROVED || a.getStatus() == AdvanceStatus.COMPLETED)
                .map(SalaryAdvance::getCreatedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        LocalDate cooldownEnds = SalaryAdvanceRules.cooldownEndsOn(lastGranted, policy.getCooldownDays(), ZONE, today);
        if (cooldownEnds != null) {
            return base.blockedReason("COOLDOWN").cooldownEndsOn(cooldownEnds).build();
        }

        BigDecimal outstandingTotal = installmentRepository.sumOutstanding(staff.getTenantId());
        BigDecimal max = SalaryAdvanceRules.eligibleMax(policy.getPoolAmount(), outstandingTotal, outstanding,
                staff.getSalary(), policy.getMaxPoolSharePercent(), policy.getMaxSalaryMultiple());
        if (max.compareTo(BigDecimal.ONE) < 0) {
            boolean poolEmpty = SalaryAdvanceRules.poolAvailable(policy.getPoolAmount(), outstandingTotal).compareTo(BigDecimal.ONE) < 0;
            return base.blockedReason(poolEmpty ? "POOL_EXHAUSTED" : "AT_LIMIT").build();
        }
        return base.canRequest(true).blockedReason("NONE").maxAmount(max).build();
    }

    private static String blockedMessage(AdvanceEligibilityResponse e) {
        return switch (e.getBlockedReason()) {
            case "NOT_CONFIGURED" -> "Salary advances are not set up yet. Please ask your manager.";
            case "DISABLED" -> "Salary advance requests are paused right now.";
            case "PENDING_REQUEST" -> "You already have a request waiting for a decision.";
            case "COOLDOWN" -> "You can request another advance from " + e.getCooldownEndsOn() + ".";
            case "AT_LIMIT" -> "You have reached your advance limit until earlier advances are repaid.";
            case "POOL_EXHAUSTED" -> "The advance budget is fully used right now. Please try again after repayments.";
            default -> "You cannot request an advance right now.";
        };
    }

    // ------------------------------------------------------------------ mapping

    private EmployeeAdvanceSummary summarize(
            Staff s, List<SalaryAdvance> theirs, BigDecimal outstanding, BigDecimal outstandingTotal,
            SalaryAdvancePolicy policy, String branchName) {
        Instant windowStart = Instant.now().minus(SalaryAdvanceRules.REPEAT_WINDOW_DAYS, ChronoUnit.DAYS);
        int recent = (int) theirs.stream()
                .filter(a -> a.getStatus() != AdvanceStatus.CANCELLED)
                .filter(a -> a.getCreatedAt() != null && a.getCreatedAt().isAfter(windowStart))
                .count();
        BigDecimal pool = policy != null ? policy.getPoolAmount() : BigDecimal.ZERO;
        BigDecimal eligible = policy != null && policy.isEnabled()
                ? SalaryAdvanceRules.eligibleMax(pool, outstandingTotal, outstanding, s.getSalary(),
                        policy.getMaxPoolSharePercent(), policy.getMaxSalaryMultiple())
                : BigDecimal.ZERO.setScale(2);
        LocalDate today = LocalDate.now(ZONE);
        LocalDate monthStart = SalaryAdvanceRules.startOfMonth(today);
        int presentMtd = (int) attendanceRepository
                .findByStaffIdAndWorkDateBetweenOrderByWorkDateDesc(s.getId(), monthStart, today).stream()
                .filter(r -> r.getEntryTime() != null)
                .count();
        double leaveMtd = approvedLeaveDays(s.getId(), monthStart, today);
        BigDecimal monthly = s.getSalary() != null ? SalaryAdvanceRules.money(s.getSalary()) : null;
        BigDecimal proRated = StaffSalaryProRating.proRated(monthly, presentMtd, leaveMtd, today);
        return EmployeeAdvanceSummary.builder()
                .staffId(s.getId())
                .staffName(s.getName())
                .branchId(s.getBranchId())
                .branchName(branchName)
                .role(s.getRole() != null ? s.getRole().name() : null)
                .active(s.isActive())
                .monthlySalary(monthly)
                .proRatedSalary(proRated)
                .requestCount((int) theirs.stream().filter(a -> a.getStatus() != AdvanceStatus.CANCELLED).count())
                .approvedCount((int) theirs.stream()
                        .filter(a -> a.getStatus() == AdvanceStatus.APPROVED || a.getStatus() == AdvanceStatus.COMPLETED).count())
                .rejectedCount((int) theirs.stream().filter(a -> a.getStatus() == AdvanceStatus.REJECTED).count())
                .totalRequested(SalaryAdvanceRules.money(theirs.stream()
                        .filter(a -> a.getStatus() != AdvanceStatus.CANCELLED)
                        .map(SalaryAdvance::getRequestedAmount).reduce(BigDecimal.ZERO, BigDecimal::add)))
                .totalApproved(SalaryAdvanceRules.money(theirs.stream()
                        .filter(a -> a.getApprovedAmount() != null)
                        .map(SalaryAdvance::getApprovedAmount).reduce(BigDecimal.ZERO, BigDecimal::add)))
                .outstanding(SalaryAdvanceRules.money(outstanding))
                .poolSharePct(SalaryAdvanceRules.percentOf(outstanding, pool))
                .lastRequestedAt(theirs.stream().map(SalaryAdvance::getCreatedAt).filter(Objects::nonNull)
                        .max(Comparator.naturalOrder()).orElse(null))
                .requestsLast180Days(recent)
                .repeatRequester(SalaryAdvanceRules.isRepeatRequester(recent))
                .eligibleMax(eligible)
                .build();
    }

    private List<AdvanceResponse> toResponses(List<SalaryAdvance> advances, boolean withRequesterHistory) {
        if (advances.isEmpty()) {
            return List.of();
        }
        Set<UUID> staffIds = advances.stream().map(SalaryAdvance::getStaffId).collect(Collectors.toSet());
        Map<UUID, Staff> staff = staffRepository.findAllById(staffIds).stream()
                .collect(Collectors.toMap(Staff::getId, Function.identity()));
        Map<UUID, String> branchNames = branchNames(advances.get(0).getTenantId());
        Set<UUID> deciderIds = advances.stream().map(SalaryAdvance::getDecidedByUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> deciderNames = userRepository.findAllById(deciderIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getName() != null ? u.getName() : "—"));
        Map<UUID, List<SalaryAdvanceInstallment>> installments = installmentRepository
                .findByAdvanceIdInOrderByDueMonthAsc(advances.stream().map(SalaryAdvance::getId).toList()).stream()
                .collect(Collectors.groupingBy(SalaryAdvanceInstallment::getAdvanceId));

        Map<UUID, EmployeeAdvanceSummary> history = new HashMap<>();
        if (withRequesterHistory && advances.stream().anyMatch(a -> a.getStatus() == AdvanceStatus.PENDING)) {
            UUID tenantId = advances.get(0).getTenantId();
            SalaryAdvancePolicy policy = policyRepository.findByTenantId(tenantId).orElse(null);
            BigDecimal outstandingTotal = installmentRepository.sumOutstanding(tenantId);
            Map<UUID, List<SalaryAdvance>> all = advanceRepository.findByStaffIdIn(staffIds).stream()
                    .collect(Collectors.groupingBy(SalaryAdvance::getStaffId));
            for (UUID id : staffIds) {
                Staff s = staff.get(id);
                if (s != null) {
                    history.put(id, summarize(s, all.getOrDefault(id, List.of()),
                            installmentRepository.sumOutstandingForStaff(id), outstandingTotal, policy, branchNames.get(s.getBranchId())));
                }
            }
        }

        List<AdvanceResponse> out = new ArrayList<>();
        for (SalaryAdvance a : advances) {
            List<SalaryAdvanceInstallment> parts = installments.getOrDefault(a.getId(), List.of());
            Staff s = staff.get(a.getStaffId());
            out.add(AdvanceResponse.builder()
                    .id(a.getId())
                    .staffId(a.getStaffId())
                    .staffName(s != null ? s.getName() : "—")
                    .branchId(a.getBranchId())
                    .branchName(branchNames.get(a.getBranchId()))
                    .requestedAmount(a.getRequestedAmount())
                    .requestedInstallments(a.getRequestedInstallments())
                    .reason(a.getReason())
                    .status(a.getStatus())
                    .approvedAmount(a.getApprovedAmount())
                    .approvedInstallments(a.getApprovedInstallments())
                    .firstDeductionMonth(a.getFirstDeductionMonth())
                    .decisionNote(a.getDecisionNote())
                    .decidedAt(a.getDecidedAt())
                    .decidedByName(a.getDecidedByUserId() != null ? deciderNames.get(a.getDecidedByUserId()) : null)
                    .createdAt(a.getCreatedAt())
                    .outstanding(SalaryAdvanceRules.money(sum(parts, InstallmentStatus.SCHEDULED)))
                    .settled(SalaryAdvanceRules.money(sum(parts, InstallmentStatus.DEDUCTED).add(sum(parts, InstallmentStatus.WAIVED))))
                    .installments(parts.stream().map(this::toInstallment).toList())
                    .requesterHistory(a.getStatus() == AdvanceStatus.PENDING ? history.get(a.getStaffId()) : null)
                    .recordedOnBehalf(isRecordedOnBehalf(a, s))
                    .build());
        }
        return out;
    }

    private static boolean isRecordedOnBehalf(SalaryAdvance advance, Staff staff) {
        UUID creator = advance.getCreatedByUserId();
        if (creator == null) {
            return false;
        }
        if (staff != null && staff.getUserId() != null) {
            return !creator.equals(staff.getUserId());
        }
        return advance.getDecidedAt() != null && advance.getCreatedAt() != null
                && !advance.getDecidedAt().isBefore(advance.getCreatedAt());
    }

    private AdvanceInstallmentResponse toInstallment(SalaryAdvanceInstallment i) {
        return AdvanceInstallmentResponse.builder()
                .id(i.getId())
                .advanceId(i.getAdvanceId())
                .dueMonth(i.getDueMonth())
                .amount(SalaryAdvanceRules.money(i.getAmount()))
                .status(i.getStatus())
                .settledAt(i.getSettledAt())
                .note(i.getNote())
                .build();
    }

    private AdvancePolicyResponse policyResponse(SalaryAdvancePolicy p) {
        if (p == null) {
            return AdvancePolicyResponse.builder()
                    .poolAmount(BigDecimal.ZERO.setScale(2))
                    .maxPoolSharePercent(25)
                    .maxSalaryMultiple(BigDecimal.ONE.setScale(2))
                    .maxInstallments(6)
                    .cooldownDays(30)
                    .enabled(true)
                    .configured(false)
                    .build();
        }
        return AdvancePolicyResponse.builder()
                .poolAmount(SalaryAdvanceRules.money(p.getPoolAmount()))
                .maxPoolSharePercent(p.getMaxPoolSharePercent())
                .maxSalaryMultiple(p.getMaxSalaryMultiple())
                .maxInstallments(p.getMaxInstallments())
                .cooldownDays(p.getCooldownDays())
                .enabled(p.isEnabled())
                .configured(p.getPoolAmount().signum() > 0)
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    // ------------------------------------------------------------------ helpers

    private void stampDecision(SalaryAdvance advance, String note) {
        advance.setDecisionNote(note != null && !note.isBlank() ? note.trim() : null);
        advance.setDecidedByUserId(SecurityUtils.currentUserId());
        advance.setDecidedAt(Instant.now());
    }

    private SalaryAdvancePolicy requireConfiguredPolicyForUpdate(UUID tenantId) {
        return policyRepository.findByTenantIdForUpdate(tenantId)
                .filter(p -> p.getPoolAmount().signum() > 0)
                .orElseThrow(() -> new BadRequestException("Set an advance budget before approving requests."));
    }

    private static void assertInstallmentCount(int installments, SalaryAdvancePolicy policy) {
        if (installments > policy.getMaxInstallments()) {
            throw new BadRequestException("Repayment can be spread over at most " + policy.getMaxInstallments() + " months.");
        }
    }

    private LocalDate resolveFirstDeductionMonth(LocalDate requested) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate firstMonth = requested != null
                ? SalaryAdvanceRules.startOfMonth(requested)
                : SalaryAdvanceRules.firstOfNextMonth(today);
        if (firstMonth.isBefore(SalaryAdvanceRules.startOfMonth(today))) {
            throw new BadRequestException("The first deduction cannot be in a past month.");
        }
        return firstMonth;
    }

    private void assertGrantWithinBudget(Staff staff, BigDecimal amount, SalaryAdvancePolicy policy, UUID tenantId) {
        BigDecimal outstandingTotal = installmentRepository.sumOutstanding(tenantId);
        BigDecimal staffOutstanding = installmentRepository.sumOutstandingForStaff(staff.getId());

        BigDecimal available = SalaryAdvanceRules.poolAvailable(policy.getPoolAmount(), outstandingTotal);
        if (amount.compareTo(available) > 0) {
            throw new BadRequestException("Only " + rupees(available) + " is left in the advance budget.");
        }
        BigDecimal cap = SalaryAdvanceRules.perEmployeeCap(policy.getPoolAmount(), policy.getMaxPoolSharePercent());
        if (staffOutstanding.add(amount).compareTo(cap) > 0) {
            throw new BadRequestException(staff.getName() + " already holds " + rupees(staffOutstanding)
                    + "; this would exceed the per-employee limit of " + rupees(cap) + ".");
        }
        if (staff.getSalary() != null && staff.getSalary().signum() > 0) {
            BigDecimal salaryCap = staff.getSalary().multiply(policy.getMaxSalaryMultiple());
            if (staffOutstanding.add(amount).compareTo(salaryCap) > 0) {
                throw new BadRequestException("This would take " + staff.getName() + " above " + policy.getMaxSalaryMultiple().stripTrailingZeros().toPlainString()
                        + "× their monthly salary (" + rupees(salaryCap) + ").");
            }
        }
    }

    private void createScheduledInstallments(
            UUID advanceId, UUID tenantId, UUID staffId, BigDecimal amount, int installments, LocalDate firstMonth) {
        List<BigDecimal> parts = SalaryAdvanceRules.splitInstallments(amount, installments);
        for (int i = 0; i < parts.size(); i++) {
            installmentRepository.save(SalaryAdvanceInstallment.builder()
                    .advanceId(advanceId)
                    .tenantId(tenantId)
                    .staffId(staffId)
                    .dueMonth(firstMonth.plusMonths(i))
                    .amount(parts.get(i))
                    .status(InstallmentStatus.SCHEDULED)
                    .build());
        }
    }

    private List<SalaryAdvanceInstallment> scheduledInstallments(UUID advanceId) {
        return installmentRepository.findByAdvanceIdOrderByDueMonthAsc(advanceId).stream()
                .filter(i -> i.getStatus() == InstallmentStatus.SCHEDULED)
                .toList();
    }

    private void rescheduleApprovedAdvance(SalaryAdvance advance, UpdateAdvanceRequest request, UUID tenantId) {
        SalaryAdvancePolicy policy = requireConfiguredPolicyForUpdate(tenantId);
        Staff staff = staffRepository.findById(advance.getStaffId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));

        List<SalaryAdvanceInstallment> all = installmentRepository.findByAdvanceIdOrderByDueMonthAsc(advance.getId());
        BigDecimal settled = sum(all, InstallmentStatus.DEDUCTED).add(sum(all, InstallmentStatus.WAIVED));
        BigDecimal oldScheduled = sum(all, InstallmentStatus.SCHEDULED);

        BigDecimal newTotal = SalaryAdvanceRules.money(
                request.getApprovedAmount() != null ? request.getApprovedAmount() : advance.getApprovedAmount());
        if (newTotal.compareTo(settled) < 0) {
            throw new BadRequestException("Approved amount cannot be less than " + rupees(settled) + " already recovered or waived.");
        }
        int installments = request.getInstallments() != null ? request.getInstallments() : advance.getApprovedInstallments();
        assertInstallmentCount(installments, policy);

        BigDecimal remaining = newTotal.subtract(settled);
        BigDecimal delta = remaining.subtract(oldScheduled);
        if (delta.signum() > 0) {
            assertGrantWithinBudget(staff, delta, policy, tenantId);
        }

        for (SalaryAdvanceInstallment i : scheduledInstallments(advance.getId())) {
            installmentRepository.delete(i);
        }

        LocalDate firstMonth = request.getFirstDeductionMonth() != null
                ? resolveFirstDeductionMonth(request.getFirstDeductionMonth())
                : advance.getFirstDeductionMonth() != null
                        ? advance.getFirstDeductionMonth()
                        : resolveFirstDeductionMonth(null);

        if (remaining.signum() > 0) {
            createScheduledInstallments(advance.getId(), tenantId, staff.getId(), remaining, installments, firstMonth);
            advance.setStatus(AdvanceStatus.APPROVED);
        } else {
            advance.setStatus(AdvanceStatus.COMPLETED);
        }

        advance.setApprovedAmount(newTotal);
        if (newTotal.compareTo(advance.getRequestedAmount()) > 0) {
            advance.setRequestedAmount(newTotal);
        }
        advance.setApprovedInstallments(installments);
        advance.setFirstDeductionMonth(firstMonth);
    }

    private SalaryAdvance requireAdvance(UUID tenantId, UUID advanceId) {
        return advanceRepository.findById(advanceId)
                .filter(a -> a.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Advance request not found"));
    }

    private Map<UUID, String> branchNames(UUID tenantId) {
        return branchRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(Branch::getId, Branch::getName, (a, b) -> a));
    }

    private static BigDecimal sum(List<SalaryAdvanceInstallment> list, InstallmentStatus status) {
        return list.stream().filter(i -> i.getStatus() == status)
                .map(SalaryAdvanceInstallment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
    }

    private double approvedLeaveDays(UUID staffId, LocalDate from, LocalDate to) {
        double days = 0;
        for (LeaveRecord l : leaveRepository.findByStaffIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(staffId, to, from)) {
            if (l.getStatus() != LeaveStatus.APPROVED) {
                continue;
            }
            LocalDate start = l.getStartDate().isBefore(from) ? from : l.getStartDate();
            LocalDate end = l.getEndDate().isAfter(to) ? to : l.getEndDate();
            long n = ChronoUnit.DAYS.between(start, end) + 1;
            days += n * (l.getLeaveType() == LeaveType.HALF_DAY ? 0.5 : 1.0);
        }
        return days;
    }

    private static String rupees(BigDecimal amount) {
        return "₹" + amount.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
