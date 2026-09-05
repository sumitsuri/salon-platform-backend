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

/**
 * Ensures demo-brand branches (including Mystic GP) have the full PDF rate card
 * on branch_services. DataSeeder skips catalog sync for demo-brand; without this patch
 * branches added after initial seed can have zero walk-in services.
 */
@Component
@Order(5)
@RequiredArgsConstructor
@Slf4j
public class DemoBrandCatalogPatch implements ApplicationRunner {

    private static final String TENANT_SLUG = "demo-brand";
    private static final String PATCH_VERSION = "demo-brand-pdf-catalog-v1";

    private final TenantRepository tenantRepository;
    private final RateCardCatalogSync rateCardCatalogSync;

    @Override
    public void run(ApplicationArguments args) {
        tenantRepository.findBySlug(TENANT_SLUG).ifPresent(tenant -> {
            if (PATCH_VERSION.equals(tenant.getCatalogPatchVersion())) {
                return;
            }
            log.info("Applying demo-brand PDF catalog patch ({})", PATCH_VERSION);
            rateCardCatalogSync.syncTenant(tenant.getId(), TENANT_SLUG, BigDecimal.ONE, false, null);
            tenant.setCatalogPatchVersion(PATCH_VERSION);
            tenantRepository.save(tenant);
            log.info("Demo-brand catalog synced for all branches (LIT, WEB, ALP, GP, VAR)");
        });
    }
}
