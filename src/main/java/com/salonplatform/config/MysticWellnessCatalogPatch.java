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
 * Replace Mystic Wellness (demo-brand) dummy catalog with the PDF rate card on Mantri Lithos (LIT) only.
 */
@Component
@Order(6)
@RequiredArgsConstructor
@Slf4j
public class MysticWellnessCatalogPatch implements ApplicationRunner {

    private static final String TENANT_SLUG = "demo-brand";
    private static final String PATCH_VERSION = "mystic-pdf-catalog-v2";
    private static final Set<String> LITHOS_BRANCH = Set.of("LIT");

    private final TenantRepository tenantRepository;
    private final RateCardCatalogSync rateCardCatalogSync;

    @Override
    public void run(ApplicationArguments args) {
        tenantRepository.findBySlug(TENANT_SLUG).ifPresent(tenant -> {
            if (PATCH_VERSION.equals(tenant.getCatalogPatchVersion())) {
                return;
            }
            log.info("Applying Mystic Wellness PDF catalog patch ({}) for Mantri Lithos", PATCH_VERSION);
            rateCardCatalogSync.purgeTenantCatalog(tenant.getId());
            rateCardCatalogSync.syncTenant(
                    tenant.getId(), TENANT_SLUG, BigDecimal.ONE, true, LITHOS_BRANCH);
            tenant.setCatalogPatchVersion(PATCH_VERSION);
            tenantRepository.save(tenant);
            log.info("Mystic Wellness catalog onboarded at Mantri Lithos (LIT)");
        });
    }
}
