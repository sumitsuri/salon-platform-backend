package com.salonplatform.service.scan;

import com.salonplatform.domain.entity.BranchService;
import com.salonplatform.domain.entity.SalonService;
import com.salonplatform.domain.entity.ServiceCategory;
import com.salonplatform.domain.repository.BranchServiceRepository;
import com.salonplatform.domain.repository.SalonServiceRepository;
import com.salonplatform.domain.repository.ServiceCategoryRepository;
import com.salonplatform.service.facescan.FaceScanRecommendationPlanner;
import com.salonplatform.service.scalpscan.ScalpScanRecommendationPlanner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScanBranchCatalogService {

    private final BranchServiceRepository branchServiceRepository;
    private final SalonServiceRepository salonServiceRepository;
    private final ServiceCategoryRepository categoryRepository;

    public List<FaceScanRecommendationPlanner.CatalogServiceRef> loadFaceCatalog(UUID branchId, UUID tenantId) {
        return load(branchId, tenantId, ScanCatalogFilter.Domain.FACE).stream()
                .map(r -> new FaceScanRecommendationPlanner.CatalogServiceRef(r.branchServiceId(), r.name()))
                .toList();
    }

    public List<ScalpScanRecommendationPlanner.CatalogServiceRef> loadScalpCatalog(UUID branchId, UUID tenantId) {
        return load(branchId, tenantId, ScanCatalogFilter.Domain.SCALP).stream()
                .map(r -> new ScalpScanRecommendationPlanner.CatalogServiceRef(r.branchServiceId(), r.name()))
                .toList();
    }

    public record CatalogEntry(UUID branchServiceId, String name) {}

    private List<CatalogEntry> load(UUID branchId, UUID tenantId, ScanCatalogFilter.Domain domain) {
        List<BranchService> branchServices = branchServiceRepository.findByBranchIdAndActiveTrue(branchId);
        if (branchServices.isEmpty()) {
            return List.of();
        }
        Set<UUID> serviceIds = branchServices.stream().map(BranchService::getServiceId).collect(Collectors.toSet());
        Map<UUID, SalonService> salonById = salonServiceRepository.findAllById(serviceIds).stream()
                .filter(s -> tenantId.equals(s.getTenantId()))
                .collect(Collectors.toMap(SalonService::getId, s -> s));

        Map<UUID, ServiceCategory> categories = categoryRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(ServiceCategory::getId, c -> c, (a, b) -> a));

        List<CatalogEntry> refs = new ArrayList<>();
        for (BranchService bs : branchServices) {
            SalonService salon = salonById.get(bs.getServiceId());
            if (salon == null || !salon.isActive()) {
                continue;
            }
            String name = bs.getDisplayNameOverride() != null && !bs.getDisplayNameOverride().isBlank()
                    ? bs.getDisplayNameOverride()
                    : salon.getName();
            String categoryPath = categoryPath(salon.getCategoryId(), categories);
            if (!ScanCatalogFilter.isEligible(domain, name, categoryPath)) {
                continue;
            }
            refs.add(new CatalogEntry(bs.getId(), name));
        }
        return refs;
    }

    private static String categoryPath(UUID categoryId, Map<UUID, ServiceCategory> categories) {
        ServiceCategory cat = categories.get(categoryId);
        if (cat == null) {
            return "";
        }
        if (cat.getParentCategoryId() == null) {
            return cat.getName();
        }
        ServiceCategory parent = categories.get(cat.getParentCategoryId());
        if (parent == null) {
            return cat.getName();
        }
        return parent.getName() + " / " + cat.getName();
    }
}
