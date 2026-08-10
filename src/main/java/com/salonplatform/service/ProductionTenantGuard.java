package com.salonplatform.service;

import com.salonplatform.config.ProductionDataProtectionProperties;
import com.salonplatform.domain.repository.TenantRepository;
import com.salonplatform.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Central guard for tenants that are live in production and must not be
 * overwritten by deploy-time seeders, catalog patches, or destructive admin ops.
 */
@Component
@RequiredArgsConstructor
public class ProductionTenantGuard {

    private final ProductionDataProtectionProperties properties;
    private final TenantRepository tenantRepository;

    public boolean isProtectedSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return false;
        }
        return properties.getProtectedTenantSlugs().stream()
                .anyMatch(protectedSlug -> protectedSlug.equalsIgnoreCase(slug.trim()));
    }

    public boolean isProtectedTenantId(UUID tenantId) {
        if (tenantId == null) {
            return false;
        }
        return tenantRepository.findById(tenantId)
                .map(tenant -> isProtectedSlug(tenant.getSlug()))
                .orElse(false);
    }

    /** Skip automated system writes (seeders, catalog patches) for protected tenants. */
    public boolean shouldSkipSystemMutation(String tenantSlug) {
        return properties.isEnabled() && isProtectedSlug(tenantSlug);
    }

    public boolean shouldSkipSystemMutation(UUID tenantId) {
        return properties.isEnabled() && isProtectedTenantId(tenantId);
    }

    /** Block destructive platform-admin actions on live production tenants. */
    public void assertAdminMutationAllowed(UUID tenantId) {
        if (properties.isEnabled() && isProtectedTenantId(tenantId)) {
            throw new BadRequestException(
                    "This tenant is a protected production brand and cannot be modified from platform admin.");
        }
    }

    /** Explicit allowlist for one-shot branch catalog patches on protected production tenants. */
    public boolean isBranchCatalogPatchAllowed(String patchId) {
        if (patchId == null || patchId.isBlank()) {
            return false;
        }
        return properties.getAllowedBranchCatalogPatches().stream()
                .anyMatch(allowed -> allowed.equalsIgnoreCase(patchId.trim()));
    }
}
