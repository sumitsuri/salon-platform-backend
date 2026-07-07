package com.salonplatform.service;

import com.salonplatform.domain.entity.Tenant;
import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.enums.TenantStatus;
import com.salonplatform.domain.enums.UserRole;
import com.salonplatform.domain.repository.TenantRepository;
import com.salonplatform.domain.repository.UserRepository;
import com.salonplatform.dto.tenant.CreateTenantRequest;
import com.salonplatform.dto.tenant.TenantResponse;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public TenantResponse create(CreateTenantRequest request) {
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

        return toResponse(tenant);
    }

    public List<TenantResponse> list() {
        SecurityUtils.assertPlatformAdmin();
        return tenantRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    private TenantResponse toResponse(Tenant t) {
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
}
