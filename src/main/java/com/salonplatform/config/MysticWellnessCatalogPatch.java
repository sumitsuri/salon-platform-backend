package com.salonplatform.config;

import com.salonplatform.domain.repository.TenantRepository;
import com.salonplatform.seed.RateCardCatalogSync;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Mystic Wellness PDF rate card on Mantri Lithos and sibling branches (not Varthur).
 */
@Component
@Order(6)
@RequiredArgsConstructor
@Slf4j
public class MysticWellnessCatalogPatch implements ApplicationRunner {

    private static final String TENANT_SLUG = "mystic-wellness";
    private static final String PATCH_VERSION = "mystic-wellness-pdf-catalog-v7";
    private static final String PREVIOUS_VERSION = "mystic-wellness-pdf-catalog-v6";
    /** Shared list prices and Lithos branch pricing reference. */
    private static final String REFERENCE_BRANCH = "MW02";
    /**
     * Branches on the standard Mystic Ocean PDF catalog (Mantri Lithos pricing).
     * Varthur (MW01) is excluded — separate catalog.
     */
    private static final Set<String> PDF_CATALOG_BRANCHES = Set.of("MW02", "MW03", "MW04", "MW05");

    private final TenantRepository tenantRepository;
    private final RateCardCatalogSync rateCardCatalogSync;

    @Override
    public void run(ApplicationArguments args) {
        tenantRepository.findBySlug(TENANT_SLUG).ifPresent(tenant -> {
            if (PATCH_VERSION.equals(tenant.getCatalogPatchVersion())) {
                return;
            }
            if (PREVIOUS_VERSION.equals(tenant.getCatalogPatchVersion())) {
                log.info("Applying Mystic Wellness catalog to Webcity, Golden Palms, and Alpine Pyramid ({})",
                        PATCH_VERSION);
                rateCardCatalogSync.syncTenant(
                        tenant.getId(), TENANT_SLUG, BigDecimal.ONE, true, PDF_CATALOG_BRANCHES);
                rateCardCatalogSync.syncSharedListPricesFromBranch(tenant.getId(), REFERENCE_BRANCH);
                tenant.setCatalogPatchVersion(PATCH_VERSION);
                tenantRepository.save(tenant);
                return;
            }
            log.info("Applying Mystic Wellness PDF catalog patch ({})", PATCH_VERSION);
            rateCardCatalogSync.purgeTenantCatalog(tenant.getId());
            rateCardCatalogSync.syncTenant(
                    tenant.getId(), TENANT_SLUG, BigDecimal.ONE, true, PDF_CATALOG_BRANCHES);
            rateCardCatalogSync.syncSharedListPricesFromBranch(tenant.getId(), REFERENCE_BRANCH);
            tenant.setCatalogPatchVersion(PATCH_VERSION);
            tenantRepository.save(tenant);
            log.info("Mystic Wellness catalog onboarded at branches {}", PDF_CATALOG_BRANCHES);
        });
    }
}
