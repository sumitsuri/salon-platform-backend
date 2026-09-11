package com.salonplatform.service;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.Customer;
import com.salonplatform.domain.entity.Tenant;
import com.salonplatform.domain.enums.CustomerIdentityStatus;
import com.salonplatform.domain.repository.BookingRepository;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.CustomerBranchStatsRow;
import com.salonplatform.domain.repository.CustomerLastVisitBranchRow;
import com.salonplatform.domain.repository.CustomerRepository;
import com.salonplatform.domain.repository.TenantRepository;
import com.salonplatform.dto.common.PageResponse;
import com.salonplatform.dto.customer.CreateCustomerRequest;
import com.salonplatform.dto.customer.CustomerListFilter;
import com.salonplatform.dto.customer.CustomerRegistrationCardResponse;
import com.salonplatform.dto.customer.CustomerResponse;
import com.salonplatform.dto.customer.UpdateCustomerRequest;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ForbiddenException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.repository.CustomerSpecifications;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.util.PageUtils;
import com.salonplatform.util.VisitPassUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final BranchRepository branchRepository;
    private final TenantRepository tenantRepository;
    private final BookingRepository bookingRepository;

    @Value("${app.public-frontend-base-url:http://localhost:3000}")
    private String publicFrontendBaseUrl;

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        UUID tenantId = SecurityUtils.requireTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        UUID branchId = requireBranchIdForWrite(request.getBranchId());
        boolean phoneRequired = resolvePhoneRequired(branchId, tenantId);
        String normalizedPhone = normalizePhoneOrNull(request.getPhone());

        if (phoneRequired && (normalizedPhone == null || normalizedPhone.isBlank())) {
            throw new BadRequestException("error.customer.phoneRequired");
        }
        if (normalizedPhone != null) {
            customerRepository.findByBranchIdAndPhone(branchId, normalizedPhone).ifPresent(c -> {
                throw new BadRequestException("error.customer.phoneExists");
            });
        }

        CustomerIdentityStatus status = normalizedPhone != null
                ? CustomerIdentityStatus.PHONE_VERIFIED
                : CustomerIdentityStatus.PASS_ONLY;

        String branchCode = resolveBranchCode(branchId, tenantId);

        Customer customer = Customer.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .name(request.getName().trim())
                .phone(normalizedPhone)
                .visitPassId(generateUniqueVisitPassId(tenant, branchId, branchCode))
                .identityStatus(status)
                .passPublicToken(VisitPassUtils.generatePublicToken())
                .society(request.getSociety())
                .flatUnit(request.getFlatUnit())
                .notes(request.getNotes())
                .visitCount(0)
                .lifetimeSpend(BigDecimal.ZERO)
                .whatsappOptIn(normalizedPhone != null)
                .build();

        return toResponse(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResponse update(UUID id, UpdateCustomerRequest request) {
        Customer customer = requireCustomer(id);
        String name = request.getName() != null ? request.getName().trim() : "";
        if (name.isBlank()) {
            throw new BadRequestException("error.customer.nameRequired");
        }
        customer.setName(name);
        return toScopedResponse(customerRepository.save(customer));
    }

    public CustomerResponse getById(UUID id) {
        Customer customer = requireCustomer(id);
        return toScopedResponse(customer);
    }

    public CustomerResponse findByPhone(String phone, UUID branchId) {
        UUID scopedBranchId = requireBranchScope(branchId);
        String normalized = normalizePhoneOrNull(phone);
        if (normalized == null) {
            throw new BadRequestException("error.customer.phoneInvalid");
        }
        Customer customer = customerRepository.findByBranchIdAndPhone(scopedBranchId, normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        return toResponse(customer);
    }

    public CustomerResponse findByVisitPass(String visitPassId, UUID branchId) {
        UUID scopedBranchId = requireBranchScope(branchId);
        String normalized = VisitPassUtils.normalizeVisitPassId(visitPassId);
        if (normalized == null) {
            throw new BadRequestException("error.customer.visitPassInvalid");
        }
        Customer customer = customerRepository.findByBranchIdAndVisitPassId(scopedBranchId, normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        return toResponse(customer);
    }

    public CustomerRegistrationCardResponse getRegistrationCard(UUID customerId, UUID branchId) {
        Customer customer = requireCustomer(customerId);
        UUID tenantId = SecurityUtils.requireTenantId();
        if (!customer.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Customer not found");
        }
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        Branch branch = branchId != null
                ? branchRepository.findById(branchId).filter(b -> b.getTenantId().equals(tenantId)).orElse(null)
                : null;
        return buildRegistrationCard(customer, tenant, branch);
    }

    public CustomerRegistrationCardResponse getRegistrationCardByPublicToken(String token) {
        Customer customer = customerRepository.findByPassPublicToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Pass not found"));
        Tenant tenant = tenantRepository.findById(customer.getTenantId()).orElse(null);
        return buildRegistrationCard(customer, tenant, null);
    }

    public List<CustomerResponse> search(String query, UUID branchId) {
        UUID scopedBranchId = requireBranchScope(branchId);
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return customerRepository.searchByBranch(scopedBranchId, query.trim()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** Returns up to {@code limit} customers matching a campaign audience spec (newest visits first). */
    public List<CustomerResponse> listForCampaignPreview(Specification<Customer> spec, int limit) {
        return customerRepository.findAll(
                        spec,
                        PageRequest.of(
                                0,
                                limit,
                                Sort.by(Sort.Direction.DESC, "lastVisitAt", "createdAt")))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public PageResponse<CustomerResponse> listPaged(CustomerListFilter filter) {
        UUID tenantId = SecurityUtils.requireTenantId();
        UUID managerBranch = managerBranchScope();
        if (managerBranch != null) {
            filter.setBranchId(managerBranch);
        }
        int page = PageUtils.normalizePage(filter.getPage());
        int size = PageUtils.normalizeSize(filter.getSize());
        Specification<Customer> spec = CustomerSpecifications.fromFilter(tenantId, filter);
        Page<Customer> result = customerRepository.findAll(
                spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastVisitAt", "createdAt")));
        final UUID branchForStats = managerBranch != null ? managerBranch : filter.getBranchId();
        List<Customer> customers = result.getContent();
        Map<UUID, CustomerBranchStatsRow> statsByCustomer = branchForStats != null
                ? loadBranchStats(customers.stream().map(Customer::getId).toList(), branchForStats)
                : Map.of();
        List<CustomerResponse> content = customers.stream()
                .map(c -> branchForStats == null
                        ? toResponse(c)
                        : applyBranchStats(toResponse(c), c.getId(), statsByCustomer.get(c.getId())))
                .sorted(customerListSort())
                .toList();
        Map<UUID, String> lastVisitBranchNames = resolveLastVisitBranchNames(
                content.stream()
                        .filter(c -> c.getLastVisitAt() != null)
                        .map(CustomerResponse::getId)
                        .toList(),
                branchForStats);
        if (!lastVisitBranchNames.isEmpty()) {
            content = content.stream()
                    .map(c -> {
                        String branchName = lastVisitBranchNames.get(c.getId());
                        return branchName != null
                                ? c.toBuilder().lastVisitBranchName(branchName).build()
                                : c;
                    })
                    .toList();
        }
        return PageResponse.<CustomerResponse>builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    private Comparator<CustomerResponse> customerListSort() {
        return Comparator
                .comparing(CustomerResponse::getLastVisitAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(c -> c.getVisitCount() != null ? c.getVisitCount() : 0, Comparator.reverseOrder())
                .thenComparing(c -> c.getName() != null ? c.getName().toLowerCase() : "", Comparator.naturalOrder());
    }

    private Customer requireCustomer(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        UUID tenantId = SecurityUtils.requireTenantId();
        if (!customer.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Customer not found");
        }
        UUID managerBranch = managerBranchScope();
        if (managerBranch != null && !managerBranch.equals(customer.getBranchId())) {
            throw new ResourceNotFoundException("Customer not found");
        }
        return customer;
    }

    /** Managers are pinned to JWT branch; brand admins must pass branch on lookup APIs. */
    private UUID requireBranchScope(UUID requestedBranchId) {
        if (SecurityUtils.isManagerRole()) {
            UUID managerBranch = SecurityUtils.currentUser().getBranchId();
            if (managerBranch == null) {
                throw new ForbiddenException("Branch context required");
            }
            SecurityUtils.assertBranchAccess(managerBranch);
            if (requestedBranchId != null && !requestedBranchId.equals(managerBranch)) {
                throw new ForbiddenException("Branch access denied");
            }
            return managerBranch;
        }
        SecurityUtils.assertBrandAdminOrAbove();
        if (requestedBranchId == null) {
            throw new BadRequestException("error.customer.branchRequired");
        }
        assertBranchInTenant(requestedBranchId);
        SecurityUtils.assertBranchAccess(requestedBranchId);
        return requestedBranchId;
    }

    private UUID requireBranchIdForWrite(UUID branchId) {
        if (branchId == null) {
            throw new BadRequestException("error.customer.branchRequired");
        }
        assertBranchInTenant(branchId);
        SecurityUtils.assertBranchAccess(branchId);
        if (SecurityUtils.isManagerRole()) {
            UUID managerBranch = SecurityUtils.currentUser().getBranchId();
            if (managerBranch == null || !managerBranch.equals(branchId)) {
                throw new ForbiddenException("Branch access denied");
            }
        }
        return branchId;
    }

    private void assertBranchInTenant(UUID branchId) {
        UUID tenantId = SecurityUtils.requireTenantId();
        branchRepository.findById(branchId)
                .filter(b -> b.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
    }

    private boolean resolvePhoneRequired(UUID branchId, UUID tenantId) {
        if (branchId == null) {
            return true;
        }
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
        if (!branch.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Branch not found");
        }
        SecurityUtils.assertBranchAccess(branchId);
        return branch.getPhoneNumberRequired() == null || Boolean.TRUE.equals(branch.getPhoneNumberRequired());
    }

    private String resolveBranchCode(UUID branchId, UUID tenantId) {
        if (branchId == null) {
            return VisitPassUtils.FALLBACK_BRANCH_CODE;
        }
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
        if (!branch.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Branch not found");
        }
        return VisitPassUtils.normalizeBranchCode(branch.getCode());
    }

    private String generateUniqueVisitPassId(Tenant tenant, UUID branchId, String branchCode) {
        for (int i = 0; i < 25; i++) {
            String candidate = VisitPassUtils.generateVisitPassId(tenant, branchCode);
            if (customerRepository.findByBranchIdAndVisitPassId(branchId, candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new BadRequestException("error.customer.visitPassGenerationFailed");
    }

    private static String normalizePhoneOrNull(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() == 10) {
            return digits;
        }
        if (digits.startsWith("91") && digits.length() == 12) {
            return digits.substring(2);
        }
        if (digits.startsWith("0") && digits.length() == 11) {
            return digits.substring(1);
        }
        return digits.length() >= 10 ? digits.substring(digits.length() - 10) : null;
    }

    private CustomerRegistrationCardResponse buildRegistrationCard(Customer customer, Tenant tenant, Branch branch) {
        String baseUrl = publicFrontendBaseUrl.endsWith("/")
                ? publicFrontendBaseUrl.substring(0, publicFrontendBaseUrl.length() - 1)
                : publicFrontendBaseUrl;
        String publicPassUrl = customer.getPassPublicToken() != null
                ? baseUrl + "/pass/?token=" + customer.getPassPublicToken()
                : null;
        return CustomerRegistrationCardResponse.builder()
                .tenantName(tenant != null ? tenant.getName() : null)
                .tenantLogoUrl(tenant != null ? tenant.getLogoUrl() : null)
                .primaryColor(tenant != null ? tenant.getPrimaryColor() : null)
                .branchName(branch != null ? branch.getName() : null)
                .branchAddress(branch != null ? branch.getAddress() : null)
                .customerName(customer.getName())
                .visitPassId(customer.getVisitPassId())
                .phone(customer.getPhone())
                .publicPassUrl(publicPassUrl)
                .issuedAt(customer.getCreatedAt() != null ? customer.getCreatedAt() : Instant.now())
                .build();
    }

    private CustomerResponse toScopedResponse(Customer c) {
        UUID branchId = managerBranchScope();
        CustomerResponse response;
        if (branchId == null) {
            response = toResponse(c);
        } else {
            Map<UUID, CustomerBranchStatsRow> stats = loadBranchStats(List.of(c.getId()), branchId);
            response = applyBranchStats(toResponse(c), c.getId(), stats.get(c.getId()));
        }
        if (response.getLastVisitAt() == null) {
            return response;
        }
        Map<UUID, String> branchNames = resolveLastVisitBranchNames(List.of(c.getId()), branchId);
        String branchName = branchNames.get(c.getId());
        return branchName != null
                ? response.toBuilder().lastVisitBranchName(branchName).build()
                : response;
    }

    /** Managers see visit stats for their branch only; brand admins see tenant-wide totals. */
    private UUID managerBranchScope() {
        if (!SecurityUtils.isManagerRole()) {
            return null;
        }
        return SecurityUtils.currentUser().getBranchId();
    }

    private Map<UUID, CustomerBranchStatsRow> loadBranchStats(List<UUID> customerIds, UUID branchId) {
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        return bookingRepository.aggregateBranchStatsForCustomers(customerIds, branchId).stream()
                .collect(Collectors.toMap(CustomerBranchStatsRow::getCustomerId, Function.identity()));
    }

    private Map<UUID, String> resolveLastVisitBranchNames(List<UUID> customerIds, UUID managerBranchId) {
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        if (managerBranchId != null) {
            String branchName = branchRepository.findById(managerBranchId).map(Branch::getName).orElse(null);
            if (branchName == null) {
                return Map.of();
            }
            return customerIds.stream().collect(Collectors.toMap(Function.identity(), id -> branchName));
        }
        return bookingRepository.findLastVisitBranchNamesForCustomers(customerIds).stream()
                .collect(Collectors.toMap(
                        CustomerLastVisitBranchRow::getCustomerId,
                        CustomerLastVisitBranchRow::getBranchName,
                        (a, b) -> a));
    }

    private CustomerResponse applyBranchStats(
            CustomerResponse base, UUID customerId, CustomerBranchStatsRow stats) {
        if (stats == null) {
            return base.toBuilder()
                    .visitCount(0)
                    .lifetimeSpend(BigDecimal.ZERO)
                    .lastVisitAt(null)
                    .build();
        }
        return base.toBuilder()
                .visitCount(stats.getVisitCount() != null ? stats.getVisitCount().intValue() : 0)
                .lifetimeSpend(stats.getLifetimeSpend() != null ? stats.getLifetimeSpend() : BigDecimal.ZERO)
                .lastVisitAt(stats.getLastVisitAt())
                .build();
    }

    private CustomerResponse toResponse(Customer c) {
        return CustomerResponse.builder()
                .id(c.getId())
                .branchId(c.getBranchId())
                .name(c.getName())
                .phone(c.getPhone())
                .visitPassId(c.getVisitPassId())
                .identityStatus(c.getIdentityStatus())
                .passPublicToken(c.getPassPublicToken())
                .email(c.getEmail())
                .society(c.getSociety())
                .flatUnit(c.getFlatUnit())
                .notes(c.getNotes())
                .whatsappOptIn(c.getWhatsappOptIn())
                .smsOptIn(c.getSmsOptIn())
                .visitCount(c.getVisitCount())
                .lifetimeSpend(c.getLifetimeSpend())
                .lastVisitAt(c.getLastVisitAt())
                .build();
    }
}
