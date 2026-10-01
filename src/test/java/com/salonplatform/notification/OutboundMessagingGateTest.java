package com.salonplatform.notification;

import com.salonplatform.config.DemoSandboxProperties;
import com.salonplatform.domain.entity.Tenant;
import com.salonplatform.domain.enums.OutboundMessagingMode;
import com.salonplatform.domain.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboundMessagingGateTest {

    @Mock
    private TenantRepository tenantRepository;

    private DemoSandboxProperties sandboxProperties;
    private OutboundMessagingGate gate;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sandboxProperties = new DemoSandboxProperties();
        gate = new OutboundMessagingGate(tenantRepository, sandboxProperties);
    }

    private void tenantWithMode(OutboundMessagingMode mode) {
        Tenant tenant = Tenant.builder().slug("x").name("X").build();
        tenant.setOutboundMessagingMode(mode);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    }

    @Test
    void liveBrandSends() {
        tenantWithMode(OutboundMessagingMode.LIVE);
        assertFalse(gate.shouldSimulate(tenantId, "919876500001"));
    }

    @Test
    void brandWithoutModeIsTreatedAsLive() {
        // Brands that predate the column (e.g. mystic-wellness before the backfill) must keep sending.
        tenantWithMode(null);
        assertFalse(gate.shouldSimulate(tenantId, "919876500001"));
    }

    @Test
    void newBrandsDefaultToSimulate() {
        assertEquals(OutboundMessagingMode.SIMULATE, Tenant.builder().build().getOutboundMessagingMode());
    }

    @Test
    void simulatedBrandNeverReachesProvider() {
        tenantWithMode(OutboundMessagingMode.SIMULATE);
        assertTrue(gate.shouldSimulate(tenantId, "919876500001"));
        Msg91Client.Msg91SendResult result = gate.simulatedResult(tenantId, "promo");
        assertTrue(result.success());
        assertTrue(result.messageId().startsWith("simulated-"));
    }

    @Test
    void allowListedTeamPhoneStillReceivesFromSimulatedBrand() {
        tenantWithMode(OutboundMessagingMode.SIMULATE);
        sandboxProperties.setAllowedPhones(List.of("98765 00001"));
        assertFalse(gate.shouldSimulate(tenantId, "919876500001"));
        assertTrue(gate.shouldSimulate(tenantId, "919876500002"));
    }

    @Test
    void unknownTenantIsNotSimulated() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());
        assertFalse(gate.shouldSimulate(tenantId, "919876500001"));
        assertFalse(gate.shouldSimulate(null, "919876500001"));
    }
}
