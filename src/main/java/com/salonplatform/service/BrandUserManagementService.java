package com.salonplatform.service;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.enums.UserRole;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.UserRepository;
import com.salonplatform.dto.user.CreatePlatformUserRequest;
import com.salonplatform.dto.user.PlatformUserResponse;
import com.salonplatform.dto.user.UpdatePlatformUserRequest;
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
public class BrandUserManagementService {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;

    public List<PlatformUserResponse> list() {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        return userRepository.findByTenantId(tenantId).stream()
                .filter(u -> u.getRole() != UserRole.PLATFORM_SUPER_ADMIN)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public PlatformUserResponse create(CreatePlatformUserRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();

        if (request.getRole() == UserRole.PLATFORM_SUPER_ADMIN) {
            throw new BadRequestException("Cannot create platform admin");
        }
        if (userRepository.findByTenantIdAndEmail(tenantId, request.getEmail()).isPresent()) {
            throw new BadRequestException("Email already exists");
        }
        validateBranchForRole(tenantId, request.getRole(), request.getBranchId());

        User user = userRepository.save(User.builder()
                .tenantId(tenantId)
                .branchId(request.getBranchId())
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .active(true)
                .build());
        return toResponse(user);
    }

    @Transactional
    public PlatformUserResponse update(UUID userId, UpdatePlatformUserRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        User user = requireUser(tenantId, userId);

        if (request.getName() != null) user.setName(request.getName());
        if (request.getEmail() != null) {
            userRepository.findByTenantIdAndEmail(tenantId, request.getEmail())
                    .filter(existing -> !existing.getId().equals(userId))
                    .ifPresent(u -> { throw new BadRequestException("Email already exists"); });
            user.setEmail(request.getEmail());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getRole() != null) {
            if (request.getRole() == UserRole.PLATFORM_SUPER_ADMIN) {
                throw new BadRequestException("Invalid role");
            }
            user.setRole(request.getRole());
        }
        UUID branchId = request.getBranchId() != null ? request.getBranchId() : user.getBranchId();
        validateBranchForRole(tenantId, user.getRole(), branchId);
        if (request.getBranchId() != null) user.setBranchId(request.getBranchId());
        if (request.getActive() != null) user.setActive(request.getActive());

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void deactivate(UUID userId) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        User user = requireUser(tenantId, userId);
        if (user.getRole() == UserRole.BRAND_ADMIN) {
            throw new BadRequestException("Cannot deactivate brand admin account");
        }
        user.setActive(false);
        userRepository.save(user);
    }

    private User requireUser(UUID tenantId, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!tenantId.equals(user.getTenantId())) {
            throw new ResourceNotFoundException("User not found");
        }
        return user;
    }

    private void validateBranchForRole(UUID tenantId, UserRole role, UUID branchId) {
        if (role == UserRole.SALON_MANAGER || role == UserRole.BRANCH_MANAGER) {
            if (branchId == null) throw new BadRequestException("Branch required for this role");
            Branch branch = branchRepository.findById(branchId)
                    .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
            if (!tenantId.equals(branch.getTenantId())) {
                throw new BadRequestException("Branch does not belong to your brand");
            }
        }
    }

    private PlatformUserResponse toResponse(User u) {
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
