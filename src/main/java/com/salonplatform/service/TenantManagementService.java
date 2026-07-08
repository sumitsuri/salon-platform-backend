package com.salonplatform.service;

import com.salonplatform.domain.entity.Tenant;
import com.salonplatform.domain.repository.TenantRepository;
import com.salonplatform.dto.tenant.TenantResponse;
import com.salonplatform.dto.tenant.UpdateTenantRequest;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantManagementService {

    private final TenantRepository tenantRepository;

    public TenantResponse getOwn() {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
        return toResponse(tenant);
    }

    @Transactional
    public TenantResponse update(UpdateTenantRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));

        if (request.getName() != null) tenant.setName(request.getName());
        if (request.getLogoUrl() != null) tenant.setLogoUrl(request.getLogoUrl());
        if (request.getPrimaryColor() != null) tenant.setPrimaryColor(request.getPrimaryColor());

        return toResponse(tenantRepository.save(tenant));
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
