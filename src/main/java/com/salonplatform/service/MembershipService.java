package com.salonplatform.service;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.Customer;
import com.salonplatform.domain.entity.MembershipPlan;
import com.salonplatform.domain.entity.MembershipSubscription;
import com.salonplatform.domain.enums.MembershipCadence;
import com.salonplatform.domain.enums.MembershipStatus;
import com.salonplatform.domain.enums.PromoStatus;
import com.salonplatform.domain.enums.ServiceScopeType;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.CustomerRepository;
import com.salonplatform.domain.repository.MembershipPlanRepository;
import com.salonplatform.domain.repository.MembershipSubscriptionRepository;
import com.salonplatform.dto.membership.CreateMembershipPlanRequest;
import com.salonplatform.dto.membership.MembershipPlanResponse;
import com.salonplatform.dto.membership.MembershipSubscriptionResponse;
import com.salonplatform.dto.membership.SellMembershipRequest;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.util.PromoScopeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MembershipService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final MembershipPlanRepository planRepository;
    private final MembershipSubscriptionRepository subscriptionRepository;
    private final CustomerRepository customerRepository;
    private final BranchRepository branchRepository;
    private final AuditService auditService;

    @Transactional
    public MembershipPlanResponse createPlan(CreateMembershipPlanRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        BigDecimal benefit = request.getBenefitPercent() != null
                ? request.getBenefitPercent() : new BigDecimal("10.00");
        if (benefit.compareTo(BigDecimal.ZERO) <= 0 || benefit.compareTo(new BigDecimal("100")) > 0) {
            throw new BadRequestException("Benefit percent must be between 0 and 100");
        }

        MembershipPlan plan = MembershipPlan.builder()
                .tenantId(tenantId)
                .name(request.getName().trim())
                .description(request.getDescription())
                .cadence(request.getCadence())
                .feeAmount(request.getFeeAmount())
                .benefitPercent(benefit)
                .serviceScope(request.getServiceScope() != null ? request.getServiceScope() : ServiceScopeType.ALL)
                .scopeIds(PromoScopeUtils.joinIds(request.getScopeIds()))
                .branchIds(PromoScopeUtils.joinIds(request.getBranchIds()))
                .status(request.getStatus() != null ? request.getStatus() : PromoStatus.ACTIVE)
                .createdByUserId(SecurityUtils.currentUser().getId())
                .build();
        return toPlanResponse(planRepository.save(plan));
    }

    public List<MembershipPlanResponse> listPlans() {
        UUID tenantId = SecurityUtils.requireTenantId();
        return planRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toPlanResponse)
                .collect(Collectors.toList());
    }

    public List<MembershipPlanResponse> listActivePlans() {
        UUID tenantId = SecurityUtils.requireTenantId();
        return planRepository.findByTenantIdAndStatus(tenantId, PromoStatus.ACTIVE).stream()
                .map(this::toPlanResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public MembershipPlanResponse updatePlanStatus(UUID id, PromoStatus status) {
        SecurityUtils.assertBrandAdminOrAbove();
        MembershipPlan plan = loadPlan(id);
        plan.setStatus(status);
        return toPlanResponse(planRepository.save(plan));
    }

    @Transactional
    public MembershipSubscriptionResponse sell(SellMembershipRequest request) {
        UUID tenantId = SecurityUtils.requireTenantId();
        SecurityUtils.assertBranchAccess(request.getBranchId());

        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        if (!customer.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Customer not found");
        }

        MembershipPlan plan = loadPlan(request.getPlanId());
        if (plan.getStatus() != PromoStatus.ACTIVE) {
            throw new BadRequestException("Membership plan is not active");
        }
        if (!PromoScopeUtils.branchAllowed(plan.getBranchIds(), request.getBranchId())) {
            throw new BadRequestException("Plan not available at this branch");
        }

        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

        LocalDate today = LocalDate.now(IST);
        findActive(tenantId, customer.getId()).ifPresent(existing -> {
            existing.setStatus(MembershipStatus.CANCELLED);
            subscriptionRepository.save(existing);
        });

        LocalDate endsOn = today.plusMonths(plan.getCadence() == MembershipCadence.MONTHS_12 ? 12 : 6);
        BigDecimal amount = request.getAmount() != null ? request.getAmount() : plan.getFeeAmount();

        MembershipSubscription sub = MembershipSubscription.builder()
                .tenantId(tenantId)
                .customerId(customer.getId())
                .planId(plan.getId())
                .branchId(branch.getId())
                .cardNumber(generateCardNumber())
                .startsOn(today)
                .endsOn(endsOn)
                .status(MembershipStatus.ACTIVE)
                .amountPaid(amount)
                .paymentMode(request.getPaymentMode())
                .paymentReference(request.getPaymentReference())
                .soldByUserId(SecurityUtils.currentUser().getId())
                .build();

        MembershipSubscription saved = subscriptionRepository.save(sub);
        auditService.log("SELL_MEMBERSHIP", "MembershipSubscription", saved.getId(),
                "Card " + saved.getCardNumber() + " for customer " + customer.getPhone());
        return toSubscriptionResponse(saved, customer, plan, branch);
    }

    public MembershipSubscriptionResponse getActiveForCustomer(UUID customerId) {
        UUID tenantId = SecurityUtils.requireTenantId();
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        return findActive(tenantId, customerId)
                .map(sub -> {
                    MembershipPlan plan = planRepository.findById(sub.getPlanId()).orElse(null);
                    Branch branch = branchRepository.findById(sub.getBranchId()).orElse(null);
                    return toSubscriptionResponse(sub, customer, plan, branch);
                })
                .orElse(null);
    }

    public java.util.Optional<MembershipSubscription> findActive(UUID tenantId, UUID customerId) {
        LocalDate today = LocalDate.now(IST);
        java.util.Optional<MembershipSubscription> active = subscriptionRepository
                .findFirstByTenantIdAndCustomerIdAndStatusAndEndsOnGreaterThanEqualOrderByEndsOnDesc(
                        tenantId, customerId, MembershipStatus.ACTIVE, today);
        active.ifPresent(sub -> {
            if (sub.getEndsOn().isBefore(today)) {
                sub.setStatus(MembershipStatus.EXPIRED);
                subscriptionRepository.save(sub);
            }
        });
        return subscriptionRepository
                .findFirstByTenantIdAndCustomerIdAndStatusAndEndsOnGreaterThanEqualOrderByEndsOnDesc(
                        tenantId, customerId, MembershipStatus.ACTIVE, today);
    }

    public MembershipPlan loadPlan(UUID id) {
        MembershipPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membership plan not found"));
        if (!plan.getTenantId().equals(SecurityUtils.requireTenantId())) {
            throw new ResourceNotFoundException("Membership plan not found");
        }
        return plan;
    }

    private String generateCardNumber() {
        int n = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return "MEM-" + String.format(Locale.ROOT, "%06d", n);
    }

    private MembershipPlanResponse toPlanResponse(MembershipPlan plan) {
        return MembershipPlanResponse.builder()
                .id(plan.getId())
                .name(plan.getName())
                .description(plan.getDescription())
                .cadence(plan.getCadence())
                .feeAmount(plan.getFeeAmount())
                .benefitPercent(plan.getBenefitPercent())
                .serviceScope(plan.getServiceScope())
                .scopeIds(PromoScopeUtils.parseIds(plan.getScopeIds()))
                .branchIds(PromoScopeUtils.parseIds(plan.getBranchIds()))
                .status(plan.getStatus())
                .createdAt(plan.getCreatedAt())
                .build();
    }

    private MembershipSubscriptionResponse toSubscriptionResponse(
            MembershipSubscription sub, Customer customer, MembershipPlan plan, Branch branch) {
        return MembershipSubscriptionResponse.builder()
                .id(sub.getId())
                .customerId(sub.getCustomerId())
                .customerName(customer != null ? customer.getName() : null)
                .customerPhone(customer != null ? customer.getPhone() : null)
                .planId(sub.getPlanId())
                .planName(plan != null ? plan.getName() : null)
                .benefitPercent(plan != null ? plan.getBenefitPercent() : null)
                .branchId(sub.getBranchId())
                .branchName(branch != null ? branch.getName() : null)
                .cardNumber(sub.getCardNumber())
                .startsOn(sub.getStartsOn())
                .endsOn(sub.getEndsOn())
                .status(sub.getStatus())
                .amountPaid(sub.getAmountPaid())
                .paymentMode(sub.getPaymentMode())
                .paymentReference(sub.getPaymentReference())
                .createdAt(sub.getCreatedAt())
                .build();
    }
}
