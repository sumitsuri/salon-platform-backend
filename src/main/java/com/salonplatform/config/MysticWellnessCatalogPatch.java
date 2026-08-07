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
 * Replace Mystic Wellness prod tenant catalog with the PDF rate card on Mantri Lithos (MW02) only.
 */
@Component
@Order(6)
@RequiredArgsConstructor
@Slf4j
public class MysticWellnessCatalogPatch implements ApplicationRunner {

    private static final String TENANT_SLUG = "mystic-wellness";
    private static final String PATCH_VERSION = "mystic-wellness-pdf-catalog-v2";
    private static final String PREVIOUS_VERSION = "mystic-wellness-pdf-catalog-v1";
    /** Mantri Lithos branch code on the mystic-wellness tenant (not demo-brand LIT). */
    private static final String LITHOS_BRANCH = "MW02";

    private final TenantRepository tenantRepository;
    private final RateCardCatalogSync rateCardCatalogSync;

    @Override
    public void run(ApplicationArguments args) {
        tenantRepository.findBySlug(TENANT_SLUG).ifPresent(tenant -> {
            if (PATCH_VERSION.equals(tenant.getCatalogPatchVersion())) {
                return;
            }
            if (PREVIOUS_VERSION.equals(tenant.getCatalogPatchVersion())) {
                log.info("Syncing Mystic Wellness shared list prices from Mantri Lithos ({})", LITHOS_BRANCH);
                rateCardCatalogSync.syncSharedListPricesFromBranch(tenant.getId(), LITHOS_BRANCH);
                tenant.setCatalogPatchVersion(PATCH_VERSION);
                tenantRepository.save(tenant);
                return;
            }
            log.info("Applying Mystic Wellness PDF catalog patch ({}) for Mantri Lithos", PATCH_VERSION);
            rateCardCatalogSync.purgeTenantCatalog(tenant.getId());
            rateCardCatalogSync.syncTenant(
                    tenant.getId(), TENANT_SLUG, BigDecimal.ONE, true, Set.of(LITHOS_BRANCH));
            rateCardCatalogSync.syncSharedListPricesFromBranch(tenant.getId(), LITHOS_BRANCH);
            tenant.setCatalogPatchVersion(PATCH_VERSION);
            tenantRepository.save(tenant);
            log.info("Mystic Wellness catalog onboarded at Mantri Lithos ({})", LITHOS_BRANCH);
        });
    }
}
