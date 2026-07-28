package com.salonplatform.seed;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.BranchService;
import com.salonplatform.domain.entity.SalonService;
import com.salonplatform.domain.entity.ServiceCategory;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.BranchServiceRepository;
import com.salonplatform.domain.repository.SalonServiceRepository;
import com.salonplatform.domain.repository.ServiceCategoryRepository;
import com.salonplatform.seed.RateCardCatalog.ServiceDef;
import com.salonplatform.seed.RateCardCatalog.SubCategoryDef;
import com.salonplatform.seed.RateCardCatalog.TopCategoryDef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Upserts the full Mystic Ocean rate-card hierarchy for a tenant and
 * ensures every branch has pricing for each service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateCardCatalogSync {

    private static final BigDecimal GST = new BigDecimal("18");
    private static final String SAC = "9997";

    private final ServiceCategoryRepository categoryRepository;
    private final SalonServiceRepository salonServiceRepository;
    private final BranchRepository branchRepository;
    private final BranchServiceRepository branchServiceRepository;

    @Transactional
    public List<SalonService> syncTenant(UUID tenantId, BigDecimal priceMultiplier) {
        Map<String, ServiceCategory> tops = new HashMap<>();
        Map<String, ServiceCategory> leaves = new HashMap<>();
        Set<UUID> keepCategoryIds = new HashSet<>();
        Set<UUID> keepServiceIds = new HashSet<>();
        List<SalonService> catalog = new ArrayList<>();
        Map<UUID, BigDecimal> listPriceByService = new HashMap<>();

        for (TopCategoryDef topDef : RateCardCatalog.all()) {
            ServiceCategory top = upsertTop(tenantId, topDef.name(), topDef.sortOrder());
            tops.put(topDef.name(), top);
            keepCategoryIds.add(top.getId());

            for (SubCategoryDef subDef : topDef.subs()) {
                String leafKey = topDef.name() + "::" + subDef.name();
                ServiceCategory leaf = upsertLeaf(tenantId, top.getId(), subDef.name(), subDef.sortOrder());
                leaves.put(leafKey, leaf);
                keepCategoryIds.add(leaf.getId());

                for (ServiceDef svcDef : subDef.services()) {
                    SalonService svc = upsertService(tenantId, leaf.getId(), svcDef);
                    keepServiceIds.add(svc.getId());
                    catalog.add(svc);
                    listPriceByService.put(svc.getId(), RateCardCatalog.money(svcDef.price()));
                }
            }
        }

        // Retire legacy flat catalog leftovers that are not on the rate card.
        for (ServiceCategory cat : categoryRepository.findByTenantId(tenantId)) {
            if (!keepCategoryIds.contains(cat.getId()) && cat.isActive()) {
                cat.setActive(false);
                categoryRepository.save(cat);
            }
        }
        for (SalonService svc : salonServiceRepository.findByTenantId(tenantId)) {
            if (!keepServiceIds.contains(svc.getId()) && svc.isActive()) {
                svc.setActive(false);
                salonServiceRepository.save(svc);
            }
        }

        for (Branch branch : branchRepository.findByTenantId(tenantId)) {
            BigDecimal branchExtra = "WEB".equals(branch.getCode()) ? new BigDecimal("1.1") : BigDecimal.ONE;
            BigDecimal multiplier = (priceMultiplier != null ? priceMultiplier : BigDecimal.ONE).multiply(branchExtra);
            for (SalonService svc : catalog) {
                BigDecimal list = listPriceByService.get(svc.getId());
                BigDecimal price = list.multiply(multiplier).setScale(0, RoundingMode.HALF_UP);
                upsertBranchPrice(tenantId, branch.getId(), svc.getId(), price);
            }
            // Hide pricing for retired legacy services on this branch.
            for (BranchService bs : branchServiceRepository.findByTenantIdAndBranchId(tenantId, branch.getId())) {
                if (!keepServiceIds.contains(bs.getServiceId()) && bs.isActive()) {
                    bs.setActive(false);
                    branchServiceRepository.save(bs);
                }
            }
        }

        log.info("Rate-card catalog synced for tenant {}: {} services across {} leaf categories",
                tenantId, catalog.size(), leaves.size());
        return catalog;
    }

    private ServiceCategory upsertTop(UUID tenantId, String name, int sortOrder) {
        return categoryRepository.findByTenantId(tenantId).stream()
                .filter(c -> name.equalsIgnoreCase(c.getName()) && c.getParentCategoryId() == null)
                .findFirst()
                .map(existing -> {
                    existing.setActive(true);
                    existing.setSortOrder(sortOrder);
                    existing.setParentCategoryId(null);
                    return categoryRepository.save(existing);
                })
                .orElseGet(() -> categoryRepository.save(ServiceCategory.builder()
                        .tenantId(tenantId)
                        .name(name)
                        .parentCategoryId(null)
                        .sortOrder(sortOrder)
                        .active(true)
                        .build()));
    }

    private ServiceCategory upsertLeaf(UUID tenantId, UUID parentId, String name, int sortOrder) {
        return categoryRepository.findByTenantId(tenantId).stream()
                .filter(c -> name.equalsIgnoreCase(c.getName()) && parentId.equals(c.getParentCategoryId()))
                .findFirst()
                .map(existing -> {
                    existing.setActive(true);
                    existing.setSortOrder(sortOrder);
                    existing.setParentCategoryId(parentId);
                    return categoryRepository.save(existing);
                })
                .orElseGet(() -> categoryRepository.save(ServiceCategory.builder()
                        .tenantId(tenantId)
                        .name(name)
                        .parentCategoryId(parentId)
                        .sortOrder(sortOrder)
                        .active(true)
                        .build()));
    }

    private SalonService upsertService(UUID tenantId, UUID categoryId, ServiceDef def) {
        return salonServiceRepository.findByTenantIdAndCategoryIdAndActiveTrue(tenantId, categoryId).stream()
                .filter(s -> def.name().equalsIgnoreCase(s.getName()))
                .findFirst()
                .map(existing -> {
                    existing.setActive(true);
                    existing.setDurationMinutes(def.durationMinutes());
                    existing.setGstRate(GST);
                    existing.setSacCode(SAC);
                    return salonServiceRepository.save(existing);
                })
                .orElseGet(() -> {
                    // Also reclaim inactive row with same name in category
                    return salonServiceRepository.findByTenantId(tenantId).stream()
                            .filter(s -> categoryId.equals(s.getCategoryId())
                                    && def.name().equalsIgnoreCase(s.getName()))
                            .findFirst()
                            .map(existing -> {
                                existing.setActive(true);
                                existing.setDurationMinutes(def.durationMinutes());
                                existing.setGstRate(GST);
                                existing.setSacCode(SAC);
                                return salonServiceRepository.save(existing);
                            })
                            .orElseGet(() -> salonServiceRepository.save(SalonService.builder()
                                    .tenantId(tenantId)
                                    .categoryId(categoryId)
                                    .name(def.name())
                                    .sacCode(SAC)
                                    .gstRate(GST)
                                    .durationMinutes(def.durationMinutes())
                                    .active(true)
                                    .build()));
                });
    }

    private void upsertBranchPrice(UUID tenantId, UUID branchId, UUID serviceId, BigDecimal price) {
        branchServiceRepository.findByBranchIdAndServiceId(branchId, serviceId)
                .ifPresentOrElse(existing -> {
                    if (existing.isManualPriceOverride()) {
                        // Keep CEO/admin price overrides; only ensure service stays available.
                        if (!existing.isActive()) {
                            existing.setActive(true);
                            branchServiceRepository.save(existing);
                        }
                        return;
                    }
                    existing.setPrice(price);
                    existing.setActive(true);
                    branchServiceRepository.save(existing);
                }, () -> branchServiceRepository.save(BranchService.builder()
                        .tenantId(tenantId)
                        .branchId(branchId)
                        .serviceId(serviceId)
                        .price(price)
                        .active(true)
                        .manualPriceOverride(false)
                        .build()));
    }
}
