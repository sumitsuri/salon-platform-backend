package com.salonplatform.service;

import com.salonplatform.config.ProductionDataProtectionProperties;
import com.salonplatform.domain.entity.Tenant;
import com.salonplatform.domain.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionTenantGuardTest {

    @Mock
    private TenantRepository tenantRepository;

    private ProductionDataProtectionProperties properties;
    private ProductionTenantGuard guard;

    @BeforeEach
    void setUp() {
        properties = new ProductionDataProtectionProperties();
        properties.setEnabled(true);
        properties.setProtectedTenantSlugs(List.of("mystic-wellness"));
        guard = new ProductionTenantGuard(properties, tenantRepository);
    }

    @Test
    void skipsSystemMutationForProtectedSlugWhenEnabled() {
        assertTrue(guard.shouldSkipSystemMutation("mystic-wellness"));
        assertFalse(guard.shouldSkipSystemMutation("demo-brand"));
    }

    @Test
    void doesNotSkipWhenProtectionDisabled() {
        properties.setEnabled(false);
        assertFalse(guard.shouldSkipSystemMutation("mystic-wellness"));
    }

    @Test
    void skipsSystemMutationForProtectedTenantId() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(
                Tenant.builder().id(tenantId).slug("mystic-wellness").name("Mystic Wellness").build()));

        assertTrue(guard.shouldSkipSystemMutation(tenantId));
    }
}
