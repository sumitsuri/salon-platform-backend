package com.salonplatform.seed;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.BranchService;
import com.salonplatform.domain.entity.SalonService;
import com.salonplatform.domain.entity.ServiceCategory;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.BranchServiceRepository;
import com.salonplatform.domain.repository.SalonServiceRepository;
import com.salonplatform.domain.repository.ServiceCategoryRepository;
import com.salonplatform.service.ProductionTenantGuard;
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
    private final ProductionTenantGuard productionTenantGuard;

    @Transactional
    public List<SalonService> syncTenant(UUID tenantId, String tenantSlug, BigDecimal priceMultiplier) {
        return syncTenant(tenantId, tenantSlug, priceMultiplier, false, null);
    }

    @Transactional
    public List<SalonService> syncTenant(UUID tenantId, String tenantSlug, BigDecimal priceMultiplier, boolean resetBranchOverrides) {
        return syncTenant(tenantId, tenantSlug, priceMultiplier, resetBranchOverrides, null);
    }

    /**
     * @param activeBranchCodes when non-null, only these branch codes receive active pricing;
     *                          other branches have branch_services deactivated.
     */
    @Transactional
    public List<SalonService> syncTenant(
            UUID tenantId,
            String tenantSlug,
            BigDecimal priceMultiplier,
            boolean resetBranchOverrides,
            Set<String> activeBranchCodes) {
        if (productionTenantGuard.shouldSkipSystemMutation(tenantSlug)) {
            log.info("Skipping catalog sync for protected production tenant {}", tenantSlug);
            return List.of();
        }
        Map<String, ServiceCategory> tops = new HashMap<>();
        Map<String, ServiceCategory> leaves = new HashMap<>();
        Set<UUID> keepCategoryIds = new HashSet<>();
        Set<UUID> keepServiceIds = new HashSet<>();
        List<SalonService> catalog = new ArrayList<>();
        Map<UUID, BigDecimal> listPriceByService = new HashMap<>();

        for (TopCategoryDef topDef : RateCardCatalog.forTenantSlug(tenantSlug)) {
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
            boolean branchActive = activeBranchCodes == null
                    || activeBranchCodes.isEmpty()
                    || activeBranchCodes.contains(branch.getCode());
            if (!branchActive) {
                for (BranchService bs : branchServiceRepository.findByTenantIdAndBranchId(tenantId, branch.getId())) {
                    if (bs.isActive()) {
                        bs.setActive(false);
                        branchServiceRepository.save(bs);
                    }
                }
                continue;
            }
            BigDecimal branchExtra = "WEB".equals(branch.getCode()) ? new BigDecimal("1.1") : BigDecimal.ONE;
            BigDecimal multiplier = (priceMultiplier != null ? priceMultiplier : BigDecimal.ONE).multiply(branchExtra);
            for (SalonService svc : catalog) {
                BigDecimal list = listPriceByService.get(svc.getId());
                BigDecimal price = list.multiply(multiplier).setScale(0, RoundingMode.HALF_UP);
                upsertBranchPrice(tenantId, branch.getId(), svc.getId(), price, resetBranchOverrides);
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
        BigDecimal list = RateCardCatalog.money(def.price());
        return salonServiceRepository.findByTenantIdAndCategoryIdAndActiveTrue(tenantId, categoryId).stream()
                .filter(s -> def.name().equalsIgnoreCase(s.getName()))
                .findFirst()
                .map(existing -> {
                    existing.setActive(true);
                    existing.setDurationMinutes(def.durationMinutes());
                    existing.setGstRate(GST);
                    existing.setSacCode(SAC);
                    existing.setVariablePricing(def.variablePricing());
                    existing.setListPrice(list);
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
                                existing.setVariablePricing(def.variablePricing());
                                existing.setListPrice(list);
                                return salonServiceRepository.save(existing);
                            })
                            .orElseGet(() -> salonServiceRepository.save(SalonService.builder()
                                    .tenantId(tenantId)
                                    .categoryId(categoryId)
                                    .name(def.name())
                                    .sacCode(SAC)
                                    .gstRate(GST)
                                    .durationMinutes(def.durationMinutes())
                                    .variablePricing(def.variablePricing())
                                    .listPrice(list)
                                    .active(true)
                                    .build()));
                });
    }

    private void upsertBranchPrice(UUID tenantId, UUID branchId, UUID serviceId, BigDecimal price) {
        upsertBranchPrice(tenantId, branchId, serviceId, price, false);
    }

    private void upsertBranchPrice(UUID tenantId, UUID branchId, UUID serviceId, BigDecimal price, boolean resetOverrides) {
        branchServiceRepository.findByBranchIdAndServiceId(branchId, serviceId)
                .ifPresentOrElse(existing -> {
                    if (existing.isManualPriceOverride() && !resetOverrides) {
                        if (!existing.isActive()) {
                            existing.setActive(true);
                            branchServiceRepository.save(existing);
                        }
                        return;
                    }
                    existing.setPrice(price);
                    existing.setActive(true);
                    existing.setManualPriceOverride(false);
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

    /** Deactivate all catalog rows for a tenant before a full re-onboard. */
    @Transactional
    public void purgeTenantCatalog(UUID tenantId) {
        if (productionTenantGuard.shouldSkipSystemMutation(tenantId)) {
            log.info("Skipping catalog purge for protected production tenant {}", tenantId);
            return;
        }
        for (Branch branch : branchRepository.findByTenantId(tenantId)) {
            for (BranchService bs : branchServiceRepository.findByTenantIdAndBranchId(tenantId, branch.getId())) {
                if (bs.isActive()) {
                    bs.setActive(false);
                    branchServiceRepository.save(bs);
                }
            }
        }
        for (SalonService svc : salonServiceRepository.findByTenantId(tenantId)) {
            if (svc.isActive()) {
                svc.setActive(false);
                salonServiceRepository.save(svc);
            }
        }
        for (ServiceCategory cat : categoryRepository.findByTenantId(tenantId)) {
            if (cat.isActive()) {
                cat.setActive(false);
                categoryRepository.save(cat);
            }
        }
        log.info("Purged active catalog for tenant {}", tenantId);
    }

    /** Copy active branch prices into each service's shared list price. */
    @Transactional
    public int syncSharedListPricesFromBranch(UUID tenantId, String branchCode) {
        if (productionTenantGuard.shouldSkipSystemMutation(tenantId)) {
            log.info("Skipping shared list price sync for protected production tenant {}", tenantId);
            return 0;
        }
        Branch branch = branchRepository.findByTenantId(tenantId).stream()
                .filter(b -> branchCode.equals(b.getCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Reference branch not found for tenant " + tenantId + ": " + branchCode));
        int updated = 0;
        for (BranchService bs : branchServiceRepository.findByTenantIdAndBranchId(tenantId, branch.getId())) {
            if (!bs.isActive()) {
                continue;
            }
            SalonService svc = salonServiceRepository.findById(bs.getServiceId()).orElse(null);
            if (svc == null || !svc.isActive()) {
                continue;
            }
            svc.setListPrice(bs.getPrice());
            salonServiceRepository.save(svc);
            updated++;
        }
        log.info("Synced shared list prices from branch {} for tenant {}: {} services",
                branchCode, tenantId, updated);
        return updated;
    }

    /**
     * Upserts tenant catalog entries and applies pricing for a single branch only.
     * Does not deactivate tenant services or touch other branches' branch_services rows.
     */
    @Transactional
    public int syncSingleBranchCatalog(
            UUID tenantId,
            String branchCode,
            List<TopCategoryDef> catalogDefs,
            boolean resetBranchOverrides) {
        Branch branch = branchRepository.findByTenantId(tenantId).stream()
                .filter(b -> branchCode.equals(b.getCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Branch not found for tenant " + tenantId + ": " + branchCode));

        Set<UUID> keepServiceIds = new HashSet<>();
        int branchPriceCount = 0;

        for (TopCategoryDef topDef : catalogDefs) {
            ServiceCategory top = upsertTop(tenantId, topDef.name(), topDef.sortOrder());
            for (SubCategoryDef subDef : topDef.subs()) {
                ServiceCategory leaf = upsertLeaf(tenantId, top.getId(), subDef.name(), subDef.sortOrder());
                for (ServiceDef svcDef : subDef.services()) {
                    SalonService svc = upsertService(tenantId, leaf.getId(), svcDef);
                    keepServiceIds.add(svc.getId());
                    BigDecimal price = RateCardCatalog.money(svcDef.price());
                    upsertBranchPrice(tenantId, branch.getId(), svc.getId(), price, resetBranchOverrides);
                    branchPriceCount++;
                }
            }
        }

        for (BranchService bs : branchServiceRepository.findByTenantIdAndBranchId(tenantId, branch.getId())) {
            if (!keepServiceIds.contains(bs.getServiceId()) && bs.isActive()) {
                bs.setActive(false);
                branchServiceRepository.save(bs);
            }
        }

        log.info("Branch catalog synced for tenant {} branch {}: {} priced services",
                tenantId, branchCode, branchPriceCount);
        return branchPriceCount;
    }

    /**
     * Copy tenant catalog list prices onto branches that have zero active branch_services.
     * Safe for prod: does not purge or deactivate other branches.
     */
    @Transactional
    public int backfillBranchesMissingServices(UUID tenantId, BigDecimal priceMultiplier) {
        List<SalonService> catalog = salonServiceRepository.findByTenantIdAndActiveTrue(tenantId);
        if (catalog.isEmpty()) {
            log.warn("Cannot backfill branch services — tenant {} has no active catalog", tenantId);
            return 0;
        }
        int branchesFixed = 0;
        for (Branch branch : branchRepository.findByTenantId(tenantId)) {
            if (!branchServiceRepository.findByBranchIdAndActiveTrue(branch.getId()).isEmpty()) {
                continue;
            }
            BigDecimal branchExtra = "WEB".equals(branch.getCode()) ? new BigDecimal("1.1") : BigDecimal.ONE;
            BigDecimal multiplier = (priceMultiplier != null ? priceMultiplier : BigDecimal.ONE).multiply(branchExtra);
            int priced = 0;
            for (SalonService svc : catalog) {
                BigDecimal list = svc.getListPrice();
                if (list == null || list.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                BigDecimal price = list.multiply(multiplier).setScale(0, RoundingMode.HALF_UP);
                upsertBranchPrice(tenantId, branch.getId(), svc.getId(), price, false);
                priced++;
            }
            log.info("Backfilled {} services for branch {} ({})", priced, branch.getName(), branch.getCode());
            branchesFixed++;
        }
        return branchesFixed;
    }

    @Transactional
    public int backfillBranchIfEmpty(UUID tenantId, UUID branchId, BigDecimal priceMultiplier) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new IllegalStateException("Branch not found: " + branchId));
        if (!tenantId.equals(branch.getTenantId())) {
            throw new IllegalStateException("Branch does not belong to tenant");
        }
        if (!branchServiceRepository.findByBranchIdAndActiveTrue(branchId).isEmpty()) {
            return 0;
        }
        List<SalonService> catalog = salonServiceRepository.findByTenantIdAndActiveTrue(tenantId);
        if (catalog.isEmpty()) {
            return 0;
        }
        BigDecimal branchExtra = "WEB".equals(branch.getCode()) ? new BigDecimal("1.1") : BigDecimal.ONE;
        BigDecimal multiplier = (priceMultiplier != null ? priceMultiplier : BigDecimal.ONE).multiply(branchExtra);
        int priced = 0;
        for (SalonService svc : catalog) {
            BigDecimal list = svc.getListPrice();
            if (list == null || list.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal price = list.multiply(multiplier).setScale(0, RoundingMode.HALF_UP);
            upsertBranchPrice(tenantId, branchId, svc.getId(), price, false);
            priced++;
        }
        log.info("Backfilled {} services for branch {} ({})", priced, branch.getName(), branch.getCode());
        return priced;
    }
}
