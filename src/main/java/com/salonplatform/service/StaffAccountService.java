package com.salonplatform.service;

import com.salonplatform.domain.entity.Staff;
import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.enums.UserRole;
import com.salonplatform.domain.repository.StaffRepository;
import com.salonplatform.domain.repository.UserRepository;
import com.salonplatform.dto.staff.ProvisionStaffLoginRequest;
import com.salonplatform.dto.staff.UpdateStaffLoginRequest;
import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.dto.staff.StaffLoginVaultPasswordResponse;
import com.salonplatform.dto.staff.StaffResponse;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StaffAccountService {

    private final StaffRepository staffRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;
    private final StaffSuggestedPasswordService suggestedPasswordService;
    private final StaffLoginPasswordVaultService loginPasswordVaultService;

    @Transactional
    public StaffResponse provisionLogin(UUID staffId, ProvisionStaffLoginRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new BadRequestException("Staff not found"));
        if (!staff.getTenantId().equals(tenantId)) {
            throw new BadRequestException("Staff not found");
        }
        if (!staff.isActive()) {
            throw new BadRequestException("Cannot provision login for inactive staff");
        }

        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        suggestedPasswordService.assertPasswordNotReused(tenantId, request.getPassword(), null);
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BadRequestException("Email already in use");
        }
        if (staff.getUserId() != null) {
            throw new BadRequestException("Staff already has a login — reset password via admin user tools");
        }

        User user = User.builder()
                .tenantId(tenantId)
                .branchId(staff.getBranchId())
                .name(staff.getName())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.SALON_STAFF)
                .active(true)
                .build();
        loginPasswordVaultService.storePlaintext(user, request.getPassword());
        user = userRepository.save(user);

        staff.setUserId(user.getId());
        if (request.getDesignation() != null && !request.getDesignation().isBlank()) {
            staff.setDesignation(request.getDesignation().trim());
        } else if (staff.getDesignation() == null || staff.getDesignation().isBlank()) {
            staff.setDesignation(formatDesignation(staff.getRole().name()));
        }
        staff = staffRepository.save(staff);

        return toResponse(staff);
    }

    @Transactional
    public StaffResponse updateLogin(UUID staffId, UpdateStaffLoginRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new BadRequestException("Staff not found"));
        if (!staff.getTenantId().equals(tenantId)) {
            throw new BadRequestException("Staff not found");
        }
        if (staff.getUserId() == null) {
            throw new BadRequestException("Staff has no login — create one first");
        }
        User user = userRepository.findById(staff.getUserId())
                .orElseThrow(() -> new BadRequestException("Login user not found"));

        boolean changed = false;
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
            if (!email.equals(user.getEmail())) {
                if (userRepository.findByEmail(email).filter(u -> !u.getId().equals(user.getId())).isPresent()) {
                    throw new BadRequestException("Email already in use");
                }
                user.setEmail(email);
                changed = true;
            }
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            suggestedPasswordService.assertPasswordNotReused(tenantId, request.getPassword(), user.getId());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            loginPasswordVaultService.storePlaintext(user, request.getPassword());
            changed = true;
        }
        if (request.getDesignation() != null && !request.getDesignation().isBlank()) {
            staff.setDesignation(request.getDesignation().trim());
            changed = true;
        }
        if (!changed) {
            throw new BadRequestException("Nothing to update — set a new password, email, or designation");
        }
        userRepository.save(user);
        staff = staffRepository.save(staff);
        return toResponse(staff);
    }

    private StaffResponse toResponse(Staff staff) {
        String branchName = branchRepository.findById(staff.getBranchId()).map(Branch::getName).orElse(null);
        return StaffResponse.builder()
                .id(staff.getId())
                .name(staff.getName())
                .phone(staff.getPhone())
                .branchId(staff.getBranchId())
                .branchName(branchName)
                .role(staff.getRole())
                .skills(staff.getSkills())
                .biometricId(staff.getBiometricId())
                .active(staff.isActive())
                .deactivatedAt(staff.getDeactivatedAt())
                .designation(staff.getDesignation())
                .hasStaffLogin(staff.getUserId() != null)
                .staffLoginEmail(resolveLoginEmail(staff))
                .build();
    }

    private String resolveLoginEmail(Staff staff) {
        if (staff.getUserId() == null) {
            return null;
        }
        return userRepository.findById(staff.getUserId()).map(User::getEmail).orElse(null);
    }

    @Transactional
    public void ensureDemoLogin(Staff staff, String email, String rawPassword, String designation) {
        if (staff.getUserId() != null) {
            return;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmail(normalized).isPresent()) {
            return;
        }
        User user = User.builder()
                .tenantId(staff.getTenantId())
                .branchId(staff.getBranchId())
                .name(staff.getName())
                .email(normalized)
                .password(passwordEncoder.encode(rawPassword))
                .role(UserRole.SALON_STAFF)
                .active(true)
                .build();
        loginPasswordVaultService.storePlaintext(user, rawPassword);
        user = userRepository.save(user);
        staff.setUserId(user.getId());
        staff.setDesignation(designation);
        staffRepository.save(staff);
    }

    public StaffLoginVaultPasswordResponse revealVaultPassword(UUID staffId) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new BadRequestException("Staff not found"));
        if (!staff.getTenantId().equals(tenantId) || staff.getUserId() == null) {
            throw new BadRequestException("Staff has no login");
        }
        User user = userRepository.findById(staff.getUserId())
                .orElseThrow(() -> new BadRequestException("Login user not found"));
        return loginPasswordVaultService.revealPlaintext(user)
                .map(pwd -> StaffLoginVaultPasswordResponse.builder().available(true).password(pwd).build())
                .orElseGet(() -> StaffLoginVaultPasswordResponse.builder().available(false).build());
    }

    private static String formatDesignation(String roleName) {
        return roleName.charAt(0) + roleName.substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
