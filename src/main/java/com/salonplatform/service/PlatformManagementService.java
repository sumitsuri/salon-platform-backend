package com.salonplatform.service;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.Tenant;
import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.enums.BranchStatus;
import com.salonplatform.domain.enums.TenantStatus;
import com.salonplatform.domain.enums.UserRole;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.TenantRepository;
import com.salonplatform.domain.repository.UserRepository;
import com.salonplatform.dto.branch.BranchResponse;
import com.salonplatform.dto.branch.CreateBranchRequest;
import com.salonplatform.dto.tenant.CreateTenantRequest;
import com.salonplatform.dto.tenant.TenantResponse;
import com.salonplatform.dto.user.CreatePlatformUserRequest;
import com.salonplatform.dto.user.PlatformUserResponse;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlatformManagementService {

    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        SecurityUtils.assertPlatformAdmin();
        if (tenantRepository.findBySlug(request.getSlug()).isPresent()) {
            throw new BadRequestException("Tenant slug already exists");
        }
        if (userRepository.findByEmail(request.getAdminEmail()).isPresent()) {
            throw new BadRequestException("Admin email already exists");
        }

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .primaryColor(request.getPrimaryColor() != null ? request.getPrimaryColor() : "#6366f1")
                .status(TenantStatus.ACTIVE)
                .build());

        userRepository.save(User.builder()
                .tenantId(tenant.getId())
                .name(request.getAdminName())
                .email(request.getAdminEmail())
                .password(passwordEncoder.encode(request.getAdminPassword()))
                .role(UserRole.BRAND_ADMIN)
                .active(true)
                .build());

        return toTenantResponse(tenant);
    }

    public List<TenantResponse> listTenants() {
        SecurityUtils.assertPlatformAdmin();
        return tenantRepository.findAll().stream().map(this::toTenantResponse).collect(Collectors.toList());
    }

    @Transactional
    public void deactivateTenant(UUID tenantId) {
        SecurityUtils.assertPlatformAdmin();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        tenant.setStatus(TenantStatus.SUSPENDED);
        tenantRepository.save(tenant);
        userRepository.findByTenantId(tenantId).forEach(u -> {
            u.setActive(false);
            userRepository.save(u);
        });
    }

    public List<BranchResponse> listBranches(UUID tenantId) {
        SecurityUtils.assertPlatformAdmin();
        requireTenant(tenantId);
        return branchRepository.findByTenantId(tenantId).stream()
                .map(this::toBranchResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public BranchResponse createBranch(UUID tenantId, CreateBranchRequest request) {
        SecurityUtils.assertPlatformAdmin();
        requireTenant(tenantId);
        if (branchRepository.findByTenantIdAndCode(tenantId, request.getCode()).isPresent()) {
            throw new BadRequestException("Branch code already exists for this tenant");
        }
        Branch branch = branchRepository.save(Branch.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .code(request.getCode())
                .address(request.getAddress())
                .societyDefault(request.getSocietyDefault())
                .gstin(request.getGstin())
                .phone(request.getPhone())
                .openTime(request.getOpenTime() != null ? request.getOpenTime() : "09:00")
                .closeTime(request.getCloseTime() != null ? request.getCloseTime() : "21:00")
                .monthlySalesTarget(request.getMonthlySalesTarget())
                .status(request.getStatus() != null ? request.getStatus() : BranchStatus.ACTIVE)
                .build());
        return toBranchResponse(branch);
    }

    @Transactional
    public void deactivateBranch(UUID tenantId, UUID branchId) {
        SecurityUtils.assertPlatformAdmin();
        Branch branch = requireBranch(tenantId, branchId);
        branch.setStatus(BranchStatus.INACTIVE);
        branchRepository.save(branch);
        userRepository.findByTenantIdAndBranchId(tenantId, branchId).forEach(u -> {
            u.setActive(false);
            userRepository.save(u);
        });
    }

    public List<PlatformUserResponse> listUsers(UUID tenantId) {
        SecurityUtils.assertPlatformAdmin();
        requireTenant(tenantId);
        return userRepository.findByTenantId(tenantId).stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public PlatformUserResponse createUser(UUID tenantId, CreatePlatformUserRequest request) {
        SecurityUtils.assertPlatformAdmin();
        requireTenant(tenantId);

        if (request.getRole() == UserRole.PLATFORM_SUPER_ADMIN) {
            throw new BadRequestException("Cannot create platform admin via tenant onboarding");
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BadRequestException("Email already exists");
        }

        UUID branchId = null;
        if (request.getRole() == UserRole.SALON_MANAGER || request.getRole() == UserRole.BRANCH_MANAGER) {
            if (request.getBranchId() == null) {
                throw new BadRequestException("Branch is required for branch/salon managers");
            }
            requireBranch(tenantId, request.getBranchId());
            branchId = request.getBranchId();
        }

        User user = userRepository.save(User.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .active(true)
                .build());

        return toUserResponse(user);
    }

    @Transactional
    public void deactivateUser(UUID tenantId, UUID userId) {
        SecurityUtils.assertPlatformAdmin();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getTenantId() == null || !user.getTenantId().equals(tenantId)) {
            throw new BadRequestException("User does not belong to this tenant");
        }
        user.setActive(false);
        userRepository.save(user);
    }

    private Tenant requireTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
    }

    private Branch requireBranch(UUID tenantId, UUID branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
        if (!branch.getTenantId().equals(tenantId)) {
            throw new BadRequestException("Branch does not belong to this tenant");
        }
        return branch;
    }

    private TenantResponse toTenantResponse(Tenant t) {
        return TenantResponse.builder()
                .id(t.getId())
                .name(t.getName())
                .slug(t.getSlug())
                .logoUrl(t.getLogoUrl())
                .primaryColor(t.getPrimaryColor())
                .status(t.getStatus())
                .createdAt(t.getCreatedAt())
                .build();
    }

    private BranchResponse toBranchResponse(Branch b) {
        return BranchResponse.builder()
                .id(b.getId())
                .name(b.getName())
                .code(b.getCode())
                .address(b.getAddress())
                .societyDefault(b.getSocietyDefault())
                .gstin(b.getGstin())
                .phone(b.getPhone())
                .openTime(b.getOpenTime())
                .closeTime(b.getCloseTime())
                .monthlySalesTarget(b.getMonthlySalesTarget())
                .status(b.getStatus())
                .createdAt(b.getCreatedAt())
                .build();
    }

    private PlatformUserResponse toUserResponse(User u) {
        String branchName = u.getBranchId() != null
                ? branchRepository.findById(u.getBranchId()).map(Branch::getName).orElse(null)
                : null;
        return PlatformUserResponse.builder()
                .id(u.getId())
                .name(u.getName())
                .email(u.getEmail())
                .role(u.getRole())
                .branchId(u.getBranchId())
                .branchName(branchName)
                .active(u.isActive())
                .build();
    }
}
