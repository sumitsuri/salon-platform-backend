package com.salonplatform.service;

import com.salonplatform.domain.entity.Booking;
import com.salonplatform.domain.entity.BookingLineItem;
import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.Customer;
import com.salonplatform.domain.entity.CustomerPackageEntitlement;
import com.salonplatform.domain.entity.CustomerPackageSubscription;
import com.salonplatform.domain.entity.SalonService;
import com.salonplatform.domain.entity.ServicePackagePlan;
import com.salonplatform.domain.entity.ServicePackagePlanItem;
import com.salonplatform.domain.enums.PackageRedemptionMode;
import com.salonplatform.domain.enums.PackageSubscriptionStatus;
import com.salonplatform.domain.enums.PromoStatus;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.BranchServiceRepository;
import com.salonplatform.domain.repository.CustomerPackageEntitlementRepository;
import com.salonplatform.domain.repository.CustomerPackageSubscriptionRepository;
import com.salonplatform.domain.repository.CustomerRepository;
import com.salonplatform.domain.repository.SalonServiceRepository;
import com.salonplatform.domain.repository.ServicePackagePlanItemRepository;
import com.salonplatform.domain.repository.ServicePackagePlanRepository;
import com.salonplatform.dto.common.PageResponse;
import com.salonplatform.dto.packageplan.CreateServicePackagePlanRequest;
import com.salonplatform.dto.packageplan.CustomerPackageEntitlementResponse;
import com.salonplatform.dto.packageplan.CustomerPackageSubscriptionResponse;
import com.salonplatform.dto.packageplan.PackageExpiringFilter;
import com.salonplatform.dto.packageplan.PackagePlanItemRequest;
import com.salonplatform.dto.packageplan.ServicePackagePlanItemResponse;
import com.salonplatform.dto.packageplan.ServicePackagePlanResponse;
import com.salonplatform.dto.packageplan.UpdateServicePackagePlanRequest;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ForbiddenException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.security.UserPrincipal;
import com.salonplatform.util.PageUtils;
import com.salonplatform.util.PromoScopeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServicePackageService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final ServicePackagePlanRepository planRepository;
    private final ServicePackagePlanItemRepository planItemRepository;
    private final CustomerPackageSubscriptionRepository subscriptionRepository;
    private final CustomerPackageEntitlementRepository entitlementRepository;
    private final CustomerRepository customerRepository;
    private final BranchRepository branchRepository;
    private final BranchServiceRepository branchServiceRepository;
    private final SalonServiceRepository salonServiceRepository;
    private final AuditService auditService;

    @Transactional
    public ServicePackagePlanResponse createPlan(CreateServicePackagePlanRequest request) {
        assertCanManagePackages();
        UUID tenantId = SecurityUtils.requireTenantId();
        validateBranchScope(request.getBranchIds());

        List<ServicePackagePlanItem> items = buildPlanItems(tenantId, request.getItems());
        BigDecimal listTotal = computeListTotal(tenantId, items);
        if (request.getPackagePrice().compareTo(listTotal) > 0) {
            throw new BadRequestException("Package price cannot exceed combined list price");
        }

        ServicePackagePlan plan = ServicePackagePlan.builder()
                .tenantId(tenantId)
                .name(request.getName().trim())
                .description(request.getDescription())
                .listPriceTotal(listTotal)
                .packagePrice(request.getPackagePrice().setScale(2, RoundingMode.HALF_UP))
                .validityDays(normalizeValidity(request.getValidityDays()))
                .redemptionMode(request.getRedemptionMode() != null
                        ? request.getRedemptionMode() : PackageRedemptionMode.MULTI_VISIT)
                .branchIds(PromoScopeUtils.joinIds(request.getBranchIds()))
                .status(request.getStatus() != null ? request.getStatus() : PromoStatus.ACTIVE)
                .createdByUserId(SecurityUtils.currentUser().getId())
                .build();
        plan = planRepository.save(plan);
        persistItems(plan.getId(), items);
        return toPlanResponse(plan, items);
    }

    @Transactional
    public ServicePackagePlanResponse updatePlan(UUID id, UpdateServicePackagePlanRequest request) {
        assertCanManagePackages();
        ServicePackagePlan plan = loadPlan(id);
        if (plan.getPredefinedRank() != null) {
            throw new BadRequestException("Predefined templates cannot be edited; create a custom package instead");
        }
        validateBranchScope(request.getBranchIds());

        List<ServicePackagePlanItem> items = buildPlanItems(plan.getTenantId(), request.getItems());
        BigDecimal listTotal = computeListTotal(plan.getTenantId(), items);
        if (request.getPackagePrice().compareTo(listTotal) > 0) {
            throw new BadRequestException("Package price cannot exceed combined list price");
        }

        plan.setName(request.getName().trim());
        plan.setDescription(request.getDescription());
        plan.setListPriceTotal(listTotal);
        plan.setPackagePrice(request.getPackagePrice().setScale(2, RoundingMode.HALF_UP));
        plan.setValidityDays(normalizeValidity(request.getValidityDays()));
        plan.setRedemptionMode(request.getRedemptionMode() != null
                ? request.getRedemptionMode() : PackageRedemptionMode.MULTI_VISIT);
        plan.setBranchIds(PromoScopeUtils.joinIds(request.getBranchIds()));
        plan.setStatus(request.getStatus() != null ? request.getStatus() : PromoStatus.ACTIVE);
        planRepository.save(plan);
        planItemRepository.deleteByPlanId(plan.getId());
        persistItems(plan.getId(), items);
        return toPlanResponse(plan, items);
    }

    @Transactional
    public void deletePlan(UUID id) {
        assertCanManagePackages();
        ServicePackagePlan plan = loadPlan(id);
        if (plan.getPredefinedRank() != null) {
            plan.setStatus(PromoStatus.PAUSED);
            planRepository.save(plan);
            return;
        }
        planItemRepository.deleteByPlanId(plan.getId());
        planRepository.delete(plan);
    }

    @Transactional
    public ServicePackagePlanResponse updatePlanStatus(UUID id, PromoStatus status) {
        assertCanManagePackages();
        ServicePackagePlan plan = loadPlan(id);
        plan.setStatus(status);
        return toPlanResponse(planRepository.save(plan), planItemRepository.findByPlanIdOrderBySortOrderAsc(plan.getId()));
    }

    public List<ServicePackagePlanResponse> listPlans(boolean predefinedOnly) {
        UUID tenantId = SecurityUtils.requireTenantId();
        List<ServicePackagePlan> plans = predefinedOnly
                ? planRepository.findByTenantIdAndPredefinedRankIsNotNullOrderByPredefinedRankAsc(tenantId)
                : planRepository.findByTenantIdOrderByPredefinedRankAscCreatedAtDesc(tenantId);
        return plans.stream().map(this::toPlanResponse).collect(Collectors.toList());
    }

    public List<ServicePackagePlanResponse> listActiveForBranch(UUID branchId) {
        UUID tenantId = SecurityUtils.requireTenantId();
        SecurityUtils.assertBranchAccess(branchId);
        return planRepository.findByTenantIdAndStatusOrderByPredefinedRankAscNameAsc(tenantId, PromoStatus.ACTIVE).stream()
                .filter(p -> PromoScopeUtils.branchAllowed(p.getBranchIds(), branchId))
                .filter(p -> planServicesAvailableAtBranch(p.getId(), branchId))
                .map(this::toPlanResponse)
                .collect(Collectors.toList());
    }

    public ServicePackagePlan loadPlan(UUID id) {
        ServicePackagePlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Package plan not found"));
        if (!plan.getTenantId().equals(SecurityUtils.requireTenantId())) {
            throw new ResourceNotFoundException("Package plan not found");
        }
        return plan;
    }

    public void assertPendingPackageAllowed(UUID tenantId, UUID branchId, UUID customerId, UUID pendingPlanId) {
        if (pendingPlanId == null) {
            return;
        }
        ServicePackagePlan plan = loadPlan(pendingPlanId);
        if (plan.getStatus() != PromoStatus.ACTIVE) {
            throw new BadRequestException("Package plan is not active");
        }
        if (!PromoScopeUtils.branchAllowed(plan.getBranchIds(), branchId)) {
            throw new BadRequestException("Package not available at this branch");
        }
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        if (!customer.getTenantId().equals(tenantId) || !customer.getBranchId().equals(branchId)) {
            throw new BadRequestException("error.customer.branchMismatch");
        }
        if (!planServicesAvailableAtBranch(plan.getId(), branchId)) {
            throw new BadRequestException(
                    "Package includes services that are not offered at this branch — update the plan or branch catalog");
        }
    }

    @Transactional
    public CustomerPackageSubscription recordPurchase(
            UUID tenantId,
            UUID customerId,
            UUID branchId,
            UUID planId,
            BigDecimal amountPaid,
            UUID invoiceId,
            UUID bookingId,
            UUID soldByUserId,
            UUID soldByStaffId) {
        ServicePackagePlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Package plan not found"));
        if (!plan.getTenantId().equals(tenantId) || plan.getStatus() != PromoStatus.ACTIVE) {
            throw new BadRequestException("Package plan is not active");
        }
        if (!PromoScopeUtils.branchAllowed(plan.getBranchIds(), branchId)) {
            throw new BadRequestException("Package not available at this branch");
        }

        LocalDate today = LocalDate.now(IST);
        LocalDate expiresOn = today.plusDays(normalizeValidity(plan.getValidityDays()));

        CustomerPackageSubscription sub = CustomerPackageSubscription.builder()
                .tenantId(tenantId)
                .customerId(customerId)
                .branchId(branchId)
                .planId(plan.getId())
                .planName(plan.getName())
                .redemptionMode(plan.getRedemptionMode())
                .amountPaid(amountPaid != null ? amountPaid : plan.getPackagePrice())
                .purchaseInvoiceId(invoiceId)
                .purchaseBookingId(bookingId)
                .purchasedOn(today)
                .expiresOn(expiresOn)
                .status(PackageSubscriptionStatus.ACTIVE)
                .soldByUserId(soldByUserId)
                .soldByStaffId(soldByStaffId)
                .build();
        sub = subscriptionRepository.save(sub);

        List<ServicePackagePlanItem> planItems = planItemRepository.findByPlanIdOrderBySortOrderAsc(plan.getId());
        Map<UUID, SalonService> services = loadServices(plan.getTenantId(), planItems);
        for (ServicePackagePlanItem pi : planItems) {
            SalonService svc = services.get(pi.getServiceId());
            entitlementRepository.save(CustomerPackageEntitlement.builder()
                    .subscriptionId(sub.getId())
                    .serviceId(pi.getServiceId())
                    .serviceName(svc != null ? svc.getName() : "Service")
                    .quantityTotal(pi.getQuantity())
                    .quantityRemaining(pi.getQuantity())
                    .build());
        }

        auditService.log("SELL_PACKAGE", "CustomerPackageSubscription", sub.getId(),
                plan.getName() + " for customer " + customerId);
        return sub;
    }

    public List<CustomerPackageSubscriptionResponse> listActiveForCustomer(UUID customerId) {
        UUID tenantId = SecurityUtils.requireTenantId();
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        if (!customer.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Customer not found");
        }
        if (SecurityUtils.isManagerRole()) {
            SecurityUtils.assertBranchAccess(customer.getBranchId());
        }
        expireStale(tenantId, LocalDate.now(IST));
        return subscriptionRepository
                .findByTenantIdAndCustomerIdAndStatusAndExpiresOnGreaterThanEqualOrderByExpiresOnAsc(
                        tenantId, customerId, PackageSubscriptionStatus.ACTIVE, LocalDate.now(IST))
                .stream()
                .map(this::toSubscriptionResponse)
                .collect(Collectors.toList());
    }

    public PageResponse<CustomerPackageSubscriptionResponse> listExpiring(PackageExpiringFilter filter) {
        UUID tenantId = SecurityUtils.requireTenantId();
        UserPrincipal user = SecurityUtils.currentUser();
        UUID branchId = filter.getBranchId();
        if (SecurityUtils.isBrandAdmin() || user.getRole() == com.salonplatform.domain.enums.UserRole.PLATFORM_SUPER_ADMIN) {
            if (branchId != null) {
                SecurityUtils.assertBranchAccess(branchId);
            }
        } else {
            branchId = user.getBranchId();
            if (branchId == null) {
                throw new BadRequestException("Branch context required");
            }
        }

        int within = filter.getWithinDays() != null && filter.getWithinDays() > 0 ? filter.getWithinDays() : 14;
        LocalDate today = LocalDate.now(IST);
        LocalDate from = filter.getExpiresFrom() != null ? filter.getExpiresFrom() : today;
        LocalDate to;
        if (filter.getExpiresTo() != null) {
            to = filter.getExpiresTo();
        } else {
            to = today.plusDays(within);
        }
        if (to.isBefore(from)) {
            throw new BadRequestException("Expiry window end must be on or after start");
        }
        expireStale(tenantId, today);

        List<CustomerPackageSubscription> raw = branchId != null
                ? subscriptionRepository.findExpiringForBranch(
                        tenantId, branchId, PackageSubscriptionStatus.ACTIVE, from, to)
                : subscriptionRepository.findExpiringForTenant(
                        tenantId, PackageSubscriptionStatus.ACTIVE, from, to);

        List<CustomerPackageSubscriptionResponse> mapped = raw.stream()
                .map(this::toSubscriptionResponse)
                .collect(Collectors.toList());
        return PageUtils.slice(mapped, PageUtils.normalizePage(filter.getPage()), PageUtils.normalizeSize(filter.getSize()));
    }

    /**
     * Validates redemption line and returns zero unit price for billing.
     */
    public BigDecimal resolveRedemptionUnitPrice(
            UUID tenantId, UUID branchId, UUID customerId, UUID subscriptionId, UUID serviceId, int quantity) {
        CustomerPackageSubscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Package subscription not found"));
        if (!sub.getTenantId().equals(tenantId) || !sub.getCustomerId().equals(customerId)) {
            throw new BadRequestException("Package does not belong to this customer");
        }
        if (!sub.getBranchId().equals(branchId)) {
            throw new BadRequestException("Package was sold at a different branch");
        }
        ensureSubscriptionRedeemable(sub);

        CustomerPackageEntitlement ent = entitlementRepository.findBySubscriptionIdAndServiceId(subscriptionId, serviceId)
                .orElseThrow(() -> new BadRequestException("Service is not included in this package"));
        if (ent.getQuantityRemaining() < quantity) {
            throw new BadRequestException("Not enough package sessions remaining for this service");
        }
        return BigDecimal.ZERO;
    }

    @Transactional
    public void applyRedemptionsAfterPayment(Booking booking, List<BookingLineItem> lines) {
        Map<UUID, List<BookingLineItem>> bySub = lines.stream()
                .filter(l -> l.getPackageSubscriptionId() != null)
                .collect(Collectors.groupingBy(BookingLineItem::getPackageSubscriptionId));

        for (Map.Entry<UUID, List<BookingLineItem>> entry : bySub.entrySet()) {
            UUID subId = entry.getKey();
            CustomerPackageSubscription sub = subscriptionRepository.findById(subId)
                    .orElseThrow(() -> new ResourceNotFoundException("Package subscription not found"));
            ensureSubscriptionRedeemable(sub);

            for (BookingLineItem line : entry.getValue()) {
                int qty = line.getQuantity() != null ? line.getQuantity() : 1;
                CustomerPackageEntitlement ent = entitlementRepository
                        .findBySubscriptionIdAndServiceId(subId, line.getServiceId())
                        .orElseThrow(() -> new BadRequestException("Invalid package redemption line"));
                if (ent.getQuantityRemaining() < qty) {
                    throw new BadRequestException("Package redemption exceeds remaining quantity");
                }
                ent.setQuantityRemaining(ent.getQuantityRemaining() - qty);
                entitlementRepository.save(ent);
            }

            refreshSubscriptionStatus(sub);
            if (sub.getRedemptionMode() == PackageRedemptionMode.SINGLE_VISIT) {
                List<CustomerPackageEntitlement> remaining = entitlementRepository.findBySubscriptionIdOrderByServiceNameAsc(subId);
                boolean anyLeft = remaining.stream().anyMatch(e -> e.getQuantityRemaining() > 0);
                if (anyLeft) {
                    throw new BadRequestException(
                            "Single-visit package requires all services to be redeemed on this bill");
                }
            }
        }
    }

    private void refreshSubscriptionStatus(CustomerPackageSubscription sub) {
        List<CustomerPackageEntitlement> ents = entitlementRepository.findBySubscriptionIdOrderByServiceNameAsc(sub.getId());
        boolean anyLeft = ents.stream().anyMatch(e -> e.getQuantityRemaining() > 0);
        if (!anyLeft) {
            sub.setStatus(PackageSubscriptionStatus.COMPLETED);
            subscriptionRepository.save(sub);
        }
    }

    private void ensureSubscriptionRedeemable(CustomerPackageSubscription sub) {
        LocalDate today = LocalDate.now(IST);
        if (sub.getStatus() != PackageSubscriptionStatus.ACTIVE) {
            throw new BadRequestException("Package is not active");
        }
        if (sub.getExpiresOn().isBefore(today)) {
            sub.setStatus(PackageSubscriptionStatus.EXPIRED);
            subscriptionRepository.save(sub);
            throw new BadRequestException("Package has expired");
        }
    }

    private void expireStale(UUID tenantId, LocalDate today) {
        subscriptionRepository.findExpiredActive(tenantId, PackageSubscriptionStatus.ACTIVE, today)
                .forEach(s -> {
                    s.setStatus(PackageSubscriptionStatus.EXPIRED);
                    subscriptionRepository.save(s);
                });
    }

    private void assertCanManagePackages() {
        UserPrincipal user = SecurityUtils.currentUser();
        if (SecurityUtils.isBrandAdmin() || user.getRole() == com.salonplatform.domain.enums.UserRole.PLATFORM_SUPER_ADMIN) {
            return;
        }
        if (SecurityUtils.isManagerRole()) {
            return;
        }
        throw new ForbiddenException("Manager access required");
    }

    private void validateBranchScope(List<UUID> branchIds) {
        if (branchIds == null || branchIds.isEmpty()) {
            return;
        }
        if (SecurityUtils.isManagerRole() && !SecurityUtils.isBrandAdmin()) {
            UUID mgrBranch = SecurityUtils.currentUser().getBranchId();
            for (UUID id : branchIds) {
                if (mgrBranch == null || !mgrBranch.equals(id)) {
                    throw new ForbiddenException("Managers can only create packages for their branch");
                }
            }
        }
        for (UUID id : branchIds) {
            SecurityUtils.assertBranchAccess(id);
        }
    }

    private int normalizeValidity(Integer days) {
        if (days == null || days < 1) {
            return 90;
        }
        return Math.min(days, 730);
    }

    private List<ServicePackagePlanItem> buildPlanItems(UUID tenantId, List<PackagePlanItemRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BadRequestException("Package must include at least one service");
        }
        List<ServicePackagePlanItem> items = new ArrayList<>();
        int order = 0;
        for (PackagePlanItemRequest req : requests) {
            SalonService svc = salonServiceRepository.findById(req.getServiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
            if (!svc.getTenantId().equals(tenantId)) {
                throw new ResourceNotFoundException("Service not found");
            }
            int qty = req.getQuantity() != null && req.getQuantity() > 0 ? req.getQuantity() : 1;
            items.add(ServicePackagePlanItem.builder()
                    .serviceId(svc.getId())
                    .quantity(qty)
                    .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : order++)
                    .build());
        }
        return items;
    }

    private BigDecimal computeListTotal(UUID tenantId, List<ServicePackagePlanItem> items) {
        Map<UUID, SalonService> services = loadServices(tenantId, items);
        BigDecimal total = BigDecimal.ZERO;
        for (ServicePackagePlanItem item : items) {
            SalonService svc = services.get(item.getServiceId());
            if (svc == null || svc.getListPrice() == null) {
                continue;
            }
            total = total.add(svc.getListPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private Map<UUID, SalonService> loadServices(UUID tenantId, List<ServicePackagePlanItem> items) {
        Map<UUID, SalonService> map = new HashMap<>();
        for (ServicePackagePlanItem item : items) {
            salonServiceRepository.findById(item.getServiceId()).ifPresent(s -> {
                if (s.getTenantId().equals(tenantId)) {
                    map.put(s.getId(), s);
                }
            });
        }
        return map;
    }

    private boolean planServicesAvailableAtBranch(UUID planId, UUID branchId) {
        List<ServicePackagePlanItem> items = planItemRepository.findByPlanIdOrderBySortOrderAsc(planId);
        if (items.isEmpty()) {
            return false;
        }
        for (ServicePackagePlanItem item : items) {
            if (branchServiceRepository.findByBranchIdAndServiceId(branchId, item.getServiceId()).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void persistItems(UUID planId, List<ServicePackagePlanItem> items) {
        for (ServicePackagePlanItem item : items) {
            item.setPlanId(planId);
            planItemRepository.save(item);
        }
    }

    private ServicePackagePlanResponse toPlanResponse(ServicePackagePlan plan) {
        return toPlanResponse(plan, planItemRepository.findByPlanIdOrderBySortOrderAsc(plan.getId()));
    }

    private ServicePackagePlanResponse toPlanResponse(ServicePackagePlan plan, List<ServicePackagePlanItem> items) {
        Map<UUID, SalonService> services = loadServices(plan.getTenantId(), items);
        List<ServicePackagePlanItemResponse> itemRows = items.stream()
                .map(i -> {
                    SalonService svc = services.get(i.getServiceId());
                    return ServicePackagePlanItemResponse.builder()
                            .id(i.getId())
                            .serviceId(i.getServiceId())
                            .serviceName(svc != null ? svc.getName() : null)
                            .listPrice(svc != null ? svc.getListPrice() : null)
                            .quantity(i.getQuantity())
                            .sortOrder(i.getSortOrder())
                            .build();
                })
                .collect(Collectors.toList());
        return ServicePackagePlanResponse.builder()
                .id(plan.getId())
                .name(plan.getName())
                .description(plan.getDescription())
                .listPriceTotal(plan.getListPriceTotal())
                .packagePrice(plan.getPackagePrice())
                .validityDays(plan.getValidityDays())
                .redemptionMode(plan.getRedemptionMode())
                .branchIds(PromoScopeUtils.parseIds(plan.getBranchIds()))
                .status(plan.getStatus())
                .predefinedRank(plan.getPredefinedRank())
                .items(itemRows)
                .createdAt(plan.getCreatedAt())
                .build();
    }

    private CustomerPackageSubscriptionResponse toSubscriptionResponse(CustomerPackageSubscription sub) {
        Customer customer = customerRepository.findById(sub.getCustomerId()).orElse(null);
        Branch branch = branchRepository.findById(sub.getBranchId()).orElse(null);
        List<CustomerPackageEntitlementResponse> ents = entitlementRepository
                .findBySubscriptionIdOrderByServiceNameAsc(sub.getId()).stream()
                .map(e -> CustomerPackageEntitlementResponse.builder()
                        .id(e.getId())
                        .serviceId(e.getServiceId())
                        .serviceName(e.getServiceName())
                        .quantityTotal(e.getQuantityTotal())
                        .quantityRemaining(e.getQuantityRemaining())
                        .build())
                .collect(Collectors.toList());
        return CustomerPackageSubscriptionResponse.builder()
                .id(sub.getId())
                .customerId(sub.getCustomerId())
                .customerName(customer != null ? customer.getName() : null)
                .customerPhone(customer != null ? customer.getPhone() : null)
                .branchId(sub.getBranchId())
                .branchName(branch != null ? branch.getName() : null)
                .planId(sub.getPlanId())
                .planName(sub.getPlanName())
                .redemptionMode(sub.getRedemptionMode())
                .amountPaid(sub.getAmountPaid())
                .purchasedOn(sub.getPurchasedOn())
                .expiresOn(sub.getExpiresOn())
                .status(sub.getStatus())
                .entitlements(ents)
                .createdAt(sub.getCreatedAt())
                .build();
    }
}
