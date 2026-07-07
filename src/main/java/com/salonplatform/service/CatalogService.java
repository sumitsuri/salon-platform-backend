package com.salonplatform.service;

import com.salonplatform.domain.entity.*;
import com.salonplatform.domain.repository.*;
import com.salonplatform.dto.catalog.*;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final ServiceCategoryRepository categoryRepository;
    private final SalonServiceRepository salonServiceRepository;
    private final BranchServiceRepository branchServiceRepository;

    @Transactional
    public ServiceCategory createCategory(CreateCategoryRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        return categoryRepository.save(ServiceCategory.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .sortOrder(request.getSortOrder())
                .active(true)
                .build());
    }

    @Transactional
    public SalonService createService(CreateServiceRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        return salonServiceRepository.save(SalonService.builder()
                .tenantId(tenantId)
                .categoryId(request.getCategoryId())
                .name(request.getName())
                .description(request.getDescription())
                .sacCode(request.getSacCode())
                .gstRate(request.getGstRate())
                .durationMinutes(request.getDurationMinutes())
                .active(true)
                .build());
    }

    @Transactional
    public BranchService setBranchPricing(UUID branchId, BranchPricingRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        SalonService svc = salonServiceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        return branchServiceRepository.findByBranchIdAndServiceId(branchId, request.getServiceId())
                .map(existing -> {
                    existing.setPrice(request.getPrice());
                    existing.setDisplayNameOverride(request.getDisplayNameOverride());
                    existing.setActive(true);
                    return branchServiceRepository.save(existing);
                })
                .orElseGet(() -> branchServiceRepository.save(BranchService.builder()
                        .tenantId(tenantId)
                        .branchId(branchId)
                        .serviceId(svc.getId())
                        .price(request.getPrice())
                        .displayNameOverride(request.getDisplayNameOverride())
                        .active(true)
                        .build()));
    }

    public List<ServiceCategory> listCategories() {
        return categoryRepository.findByTenantIdAndActiveTrueOrderBySortOrderAsc(SecurityUtils.requireTenantId());
    }

    public List<BranchServiceResponse> listBranchServices(UUID branchId) {
        SecurityUtils.assertBranchAccess(branchId);
        return branchServiceRepository.findByBranchIdAndActiveTrue(branchId).stream()
                .map(bs -> {
                    SalonService svc = salonServiceRepository.findById(bs.getServiceId()).orElse(null);
                    ServiceCategory cat = svc != null
                            ? categoryRepository.findById(svc.getCategoryId()).orElse(null) : null;
                    return BranchServiceResponse.builder()
                            .id(bs.getId())
                            .branchId(bs.getBranchId())
                            .serviceId(bs.getServiceId())
                            .serviceName(svc != null ? svc.getName() : null)
                            .categoryId(svc != null ? svc.getCategoryId() : null)
                            .categoryName(cat != null ? cat.getName() : null)
                            .price(bs.getPrice())
                            .gstRate(svc != null ? svc.getGstRate() : null)
                            .displayNameOverride(bs.getDisplayNameOverride())
                            .active(bs.isActive())
                            .build();
                })
                .collect(Collectors.toList());
    }
}
