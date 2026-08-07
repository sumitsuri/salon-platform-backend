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
 * One-time style patch: replace Velvet Scissors dummy catalog with the PDF rate card
 * and apply list prices to all three branches (Indiranagar, Koramangala, Whitefield).
 */
@Component
@Order(5)
@RequiredArgsConstructor
@Slf4j
public class VelvetScissorsCatalogPatch implements ApplicationRunner {

    private static final String TENANT_SLUG = "velvet-scissors";
    private static final String PATCH_VERSION = "velvet-pdf-catalog-v1";

    private final TenantRepository tenantRepository;
    private final RateCardCatalogSync rateCardCatalogSync;

    @Override
    public void run(ApplicationArguments args) {
        tenantRepository.findBySlug(TENANT_SLUG).ifPresent(tenant -> {
            if (PATCH_VERSION.equals(tenant.getCatalogPatchVersion())) {
                return;
            }
            log.info("Applying Velvet Scissors PDF catalog patch ({})", PATCH_VERSION);
            rateCardCatalogSync.purgeTenantCatalog(tenant.getId());
            rateCardCatalogSync.syncTenant(tenant.getId(), TENANT_SLUG, BigDecimal.ONE, true);
            tenant.setCatalogPatchVersion(PATCH_VERSION);
            tenantRepository.save(tenant);
            log.info("Velvet Scissors catalog onboarded from PDF rate card");
        });
    }
}
