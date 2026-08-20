package com.salonplatform.service;

import com.salonplatform.domain.entity.*;
import com.salonplatform.domain.repository.*;
import com.salonplatform.dto.catalog.*;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final ServiceCategoryRepository categoryRepository;
    private final SalonServiceRepository salonServiceRepository;
    private final BranchServiceRepository branchServiceRepository;
    private final BranchRepository branchRepository;

    @Transactional
    public ServiceCategory createCategory(CreateCategoryRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        if (request.getParentCategoryId() != null) {
            requireTenantCategory(request.getParentCategoryId(), tenantId);
        }
        return categoryRepository.save(ServiceCategory.builder()
                .tenantId(tenantId)
                .name(request.getName().trim())
                .parentCategoryId(request.getParentCategoryId())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .active(true)
                .build());
    }

    @Transactional
    public ServiceCategory updateCategory(UUID categoryId, UpdateCategoryRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        ServiceCategory cat = requireTenantCategory(categoryId, tenantId);
        if (request.getName() != null && !request.getName().isBlank()) {
            cat.setName(request.getName().trim());
        }
        if (request.getSortOrder() != null) {
            cat.setSortOrder(request.getSortOrder());
        }
        if (Boolean.TRUE.equals(request.getClearParent())) {
            cat.setParentCategoryId(null);
        } else if (request.getParentCategoryId() != null) {
            if (request.getParentCategoryId().equals(categoryId)) {
                throw new BadRequestException("error.catalog.categorySelfParent");
            }
            requireTenantCategory(request.getParentCategoryId(), tenantId);
            cat.setParentCategoryId(request.getParentCategoryId());
        }
        if (request.getActive() != null) {
            cat.setActive(request.getActive());
        }
        return categoryRepository.save(cat);
    }

    @Transactional
    public void deactivateCategory(UUID categoryId) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        ServiceCategory cat = requireTenantCategory(categoryId, tenantId);
        cat.setActive(false);
        categoryRepository.save(cat);
        // Soft-deactivate leaf services under this category
        for (SalonService svc : salonServiceRepository.findByTenantIdAndCategoryIdAndActiveTrue(tenantId, categoryId)) {
            svc.setActive(false);
            salonServiceRepository.save(svc);
            deactivateAllBranchAssignments(tenantId, svc.getId());
        }
        // Soft-deactivate child categories + their services
        for (ServiceCategory child : categoryRepository.findByTenantId(tenantId)) {
            if (categoryId.equals(child.getParentCategoryId()) && child.isActive()) {
                child.setActive(false);
                categoryRepository.save(child);
                for (SalonService svc : salonServiceRepository.findByTenantIdAndCategoryIdAndActiveTrue(tenantId, child.getId())) {
                    svc.setActive(false);
                    salonServiceRepository.save(svc);
                    deactivateAllBranchAssignments(tenantId, svc.getId());
                }
            }
        }
    }

    @Transactional
    public SalonService createService(CreateServiceRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        requireTenantCategory(request.getCategoryId(), tenantId);
        return salonServiceRepository.save(SalonService.builder()
                .tenantId(tenantId)
                .categoryId(request.getCategoryId())
                .name(request.getName().trim())
                .description(request.getDescription())
                .sacCode(request.getSacCode())
                .gstRate(request.getGstRate())
                .durationMinutes(request.getDurationMinutes())
                .active(true)
                .build());
    }

    @Transactional
    public SalonService updateService(UUID serviceId, UpdateServiceRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        SalonService svc = requireTenantService(serviceId, tenantId);
        if (request.getCategoryId() != null) {
            requireTenantCategory(request.getCategoryId(), tenantId);
            svc.setCategoryId(request.getCategoryId());
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            svc.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            svc.setDescription(request.getDescription());
        }
        if (request.getSacCode() != null) {
            svc.setSacCode(request.getSacCode());
        }
        if (request.getGstRate() != null) {
            svc.setGstRate(request.getGstRate());
        }
        if (request.getDurationMinutes() != null) {
            svc.setDurationMinutes(request.getDurationMinutes());
        }
        if (request.getActive() != null) {
            svc.setActive(request.getActive());
            if (!request.getActive()) {
                deactivateAllBranchAssignments(tenantId, svc.getId());
            }
        }
        return salonServiceRepository.save(svc);
    }

    @Transactional
    public void deactivateService(UUID serviceId) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        SalonService svc = requireTenantService(serviceId, tenantId);
        svc.setActive(false);
        salonServiceRepository.save(svc);
        deactivateAllBranchAssignments(tenantId, svc.getId());
    }

    @Transactional
    public BranchService setBranchPricing(UUID branchId, BranchPricingRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        SecurityUtils.assertBranchAccess(branchId);
        SalonService svc = requireTenantService(request.getServiceId(), tenantId);

        return branchServiceRepository.findByBranchIdAndServiceId(branchId, request.getServiceId())
                .map(existing -> {
                    existing.setPrice(request.getPrice());
                    existing.setDisplayNameOverride(request.getDisplayNameOverride());
                    existing.setActive(true);
                    existing.setManualPriceOverride(true);
                    return branchServiceRepository.save(existing);
                })
                .orElseGet(() -> branchServiceRepository.save(BranchService.builder()
                        .tenantId(tenantId)
                        .branchId(branchId)
                        .serviceId(svc.getId())
                        .price(request.getPrice())
                        .displayNameOverride(request.getDisplayNameOverride())
                        .active(true)
                        .manualPriceOverride(true)
                        .build()));
    }

    @Transactional
    public void removeBranchPricing(UUID branchId, UUID serviceId) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        SecurityUtils.assertBranchAccess(branchId);
        BranchService bs = branchServiceRepository.findByBranchIdAndServiceId(branchId, serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch pricing not found"));
        if (!bs.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Branch pricing not found");
        }
        bs.setActive(false);
        branchServiceRepository.save(bs);
    }

    @Transactional
    public CatalogServiceResponse setServiceBranches(UUID serviceId, SetServiceBranchesRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        SalonService svc = requireTenantService(serviceId, tenantId);

        Map<UUID, ServiceBranchPriceRequest> desired = request.getAssignments().stream()
                .collect(Collectors.toMap(ServiceBranchPriceRequest::getBranchId, Function.identity(), (a, b) -> b));

        for (Map.Entry<UUID, ServiceBranchPriceRequest> entry : desired.entrySet()) {
            UUID branchId = entry.getKey();
            ServiceBranchPriceRequest row = entry.getValue();
            SecurityUtils.assertBranchAccess(branchId);
            boolean active = row.getActive() == null || row.getActive();
            branchServiceRepository.findByBranchIdAndServiceId(branchId, serviceId)
                    .ifPresentOrElse(existing -> {
                        existing.setPrice(row.getPrice());
                        if (row.getDisplayNameOverride() != null) {
                            existing.setDisplayNameOverride(row.getDisplayNameOverride());
                        }
                        existing.setActive(active);
                        existing.setManualPriceOverride(true);
                        branchServiceRepository.save(existing);
                    }, () -> {
                        if (active) {
                            branchServiceRepository.save(BranchService.builder()
                                    .tenantId(tenantId)
                                    .branchId(branchId)
                                    .serviceId(svc.getId())
                                    .price(row.getPrice())
                                    .displayNameOverride(row.getDisplayNameOverride())
                                    .active(true)
                                    .manualPriceOverride(true)
                                    .build());
                        }
                    });
        }

        // Deactivate assignments not present in the payload (explicit replace)
        for (BranchService existing : branchServiceRepository.findByTenantIdAndServiceId(tenantId, serviceId)) {
            if (!desired.containsKey(existing.getBranchId()) && existing.isActive()) {
                existing.setActive(false);
                branchServiceRepository.save(existing);
            }
        }

        return toCatalogServiceResponse(svc, true);
    }

    public List<ServiceCategory> listCategories(boolean includeInactive) {
        UUID tenantId = SecurityUtils.requireTenantId();
        if (includeInactive) {
            return categoryRepository.findByTenantId(tenantId).stream()
                    .sorted(Comparator.comparing(ServiceCategory::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                            .thenComparing(ServiceCategory::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .collect(Collectors.toList());
        }
        return categoryRepository.findByTenantIdAndActiveTrueOrderBySortOrderAsc(tenantId);
    }

    public List<CatalogServiceResponse> listServices(boolean includeInactive) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        List<SalonService> services = includeInactive
                ? salonServiceRepository.findByTenantId(tenantId)
                : salonServiceRepository.findByTenantIdAndActiveTrue(tenantId);
        return services.stream()
                .sorted(Comparator.comparing(SalonService::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(svc -> toCatalogServiceResponse(svc, includeInactive))
                .collect(Collectors.toList());
    }

    public CatalogServiceResponse getService(UUID serviceId) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        return toCatalogServiceResponse(requireTenantService(serviceId, tenantId), true);
    }

    public List<BranchServiceResponse> listBranchServices(UUID branchId) {
        SecurityUtils.assertBranchAccess(branchId);
        return mapActiveBranchServices(branchId);
    }

    /** Public online booking — no authenticated user context. */
    public List<BranchServiceResponse> listBranchServicesPublic(UUID branchId) {
        return mapActiveBranchServices(branchId);
    }

    private List<BranchServiceResponse> mapActiveBranchServices(UUID branchId) {
        return branchServiceRepository.findByBranchIdAndActiveTrue(branchId).stream()
                .map(bs -> {
                    SalonService svc = salonServiceRepository.findById(bs.getServiceId()).orElse(null);
                    if (svc == null || !svc.isActive()) {
                        return null;
                    }
                    ServiceCategory cat = categoryRepository.findById(svc.getCategoryId()).orElse(null);
                    ServiceCategory parent = cat != null && cat.getParentCategoryId() != null
                            ? categoryRepository.findById(cat.getParentCategoryId()).orElse(null) : null;
                    return BranchServiceResponse.builder()
                            .id(bs.getId())
                            .branchId(bs.getBranchId())
                            .serviceId(bs.getServiceId())
                            .serviceName(svc.getName())
                            .categoryId(svc.getCategoryId())
                            .categoryName(cat != null ? cat.getName() : null)
                            .parentCategoryId(parent != null ? parent.getId() : (cat != null ? cat.getId() : null))
                            .parentCategoryName(parent != null ? parent.getName() : (cat != null ? cat.getName() : null))
                            .price(bs.getPrice())
                            .gstRate(svc.getGstRate())
                            .durationMinutes(svc.getDurationMinutes())
                            .variablePricing(svc.isVariablePricing())
                            .displayNameOverride(bs.getDisplayNameOverride())
                            .active(bs.isActive())
                            .build();
                })
                .filter(r -> r != null)
                .collect(Collectors.toList());
    }

    private CatalogServiceResponse toCatalogServiceResponse(SalonService svc, boolean includeInactiveBranches) {
        ServiceCategory cat = categoryRepository.findById(svc.getCategoryId()).orElse(null);
        ServiceCategory parent = cat != null && cat.getParentCategoryId() != null
                ? categoryRepository.findById(cat.getParentCategoryId()).orElse(null) : null;

        Map<UUID, String> branchNames = branchRepository.findByTenantId(svc.getTenantId()).stream()
                .collect(Collectors.toMap(Branch::getId, Branch::getName, (a, b) -> a));

        List<ServiceBranchAssignmentResponse> assignments = new ArrayList<>();
        for (BranchService bs : branchServiceRepository.findByTenantIdAndServiceId(svc.getTenantId(), svc.getId())) {
            if (!includeInactiveBranches && !bs.isActive()) {
                continue;
            }
            assignments.add(ServiceBranchAssignmentResponse.builder()
                    .branchServiceId(bs.getId())
                    .branchId(bs.getBranchId())
                    .branchName(branchNames.getOrDefault(bs.getBranchId(), "Branch"))
                    .price(bs.getPrice())
                    .displayNameOverride(bs.getDisplayNameOverride())
                    .active(bs.isActive())
                    .manualPriceOverride(bs.isManualPriceOverride())
                    .build());
        }
        assignments.sort(Comparator.comparing(ServiceBranchAssignmentResponse::getBranchName,
                Comparator.nullsLast(String::compareToIgnoreCase)));

        return CatalogServiceResponse.builder()
                .id(svc.getId())
                .name(svc.getName())
                .description(svc.getDescription())
                .categoryId(svc.getCategoryId())
                .categoryName(cat != null ? cat.getName() : null)
                .parentCategoryId(parent != null ? parent.getId() : (cat != null ? cat.getId() : null))
                .parentCategoryName(parent != null ? parent.getName() : (cat != null ? cat.getName() : null))
                .sacCode(svc.getSacCode())
                .gstRate(svc.getGstRate())
                .durationMinutes(svc.getDurationMinutes())
                .active(svc.isActive())
                .listPrice(svc.getListPrice())
                .branches(assignments)
                .build();
    }

    private void deactivateAllBranchAssignments(UUID tenantId, UUID serviceId) {
        for (BranchService bs : branchServiceRepository.findByTenantIdAndServiceId(tenantId, serviceId)) {
            if (bs.isActive()) {
                bs.setActive(false);
                branchServiceRepository.save(bs);
            }
        }
    }

    private ServiceCategory requireTenantCategory(UUID categoryId, UUID tenantId) {
        ServiceCategory cat = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        if (!cat.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Category not found");
        }
        return cat;
    }

    private SalonService requireTenantService(UUID serviceId, UUID tenantId) {
        SalonService svc = salonServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
        if (!svc.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Service not found");
        }
        return svc;
    }
}
