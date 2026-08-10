package com.salonplatform.config;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.TenantRepository;
import com.salonplatform.seed.MysticWellnessVarthurRateCardCatalog;
import com.salonplatform.seed.RateCardCatalogSync;
import com.salonplatform.service.ProductionTenantGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * One-shot Varthur (MW01) menu sync for Mystic Wellness production.
 * Does not modify other branches' pricing or deactivate tenant-wide catalog used elsewhere.
 */
@Component
@Order(7)
@RequiredArgsConstructor
@Slf4j
public class MysticWellnessVarthurCatalogPatch implements ApplicationRunner {

    public static final String PATCH_ID = "varthur-menu-v3";
    private static final String TENANT_SLUG = "mystic-wellness";
    private static final String BRANCH_CODE = "MW01";

    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final RateCardCatalogSync rateCardCatalogSync;
    private final ProductionTenantGuard productionTenantGuard;

    @Override
    public void run(ApplicationArguments args) {
        if (!productionTenantGuard.isBranchCatalogPatchAllowed(PATCH_ID)) {
            log.info("Skipping Varthur catalog patch — {} not in allowlist", PATCH_ID);
            return;
        }

        tenantRepository.findBySlug(TENANT_SLUG).ifPresent(tenant -> {
            Branch branch = branchRepository.findByTenantIdAndCode(tenant.getId(), BRANCH_CODE).orElse(null);
            if (branch == null) {
                log.warn("Varthur branch {} not found for tenant {}", BRANCH_CODE, TENANT_SLUG);
                return;
            }
            if (PATCH_ID.equals(branch.getCatalogPatchVersion())) {
                log.info("Varthur catalog patch {} already applied on branch {}", PATCH_ID, BRANCH_CODE);
                return;
            }

            int count = rateCardCatalogSync.syncSingleBranchCatalog(
                    tenant.getId(),
                    BRANCH_CODE,
                    MysticWellnessVarthurRateCardCatalog.all(),
                    true);

            branch.setCatalogPatchVersion(PATCH_ID);
            branchRepository.save(branch);
            log.info("Applied Varthur catalog patch {} on {} ({} services priced)", PATCH_ID, BRANCH_CODE, count);
        });
    }
}
