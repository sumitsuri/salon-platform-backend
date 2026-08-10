package com.salonplatform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Guards live production tenants from automated seed/patch mutations on deploy.
 * Enabled in {@code application-prod.yml}; off locally so dev seeding still works.
 */
@Component
@ConfigurationProperties(prefix = "app.production-data-protection")
@Getter
@Setter
public class ProductionDataProtectionProperties {

    /** When true, system seeders/patches skip protected tenant slugs. */
    private boolean enabled = false;

    /** Tenant slugs whose data must not be mutated by startup jobs (e.g. mystic-wellness). */
    private List<String> protectedTenantSlugs = new ArrayList<>(List.of("mystic-wellness"));
}
