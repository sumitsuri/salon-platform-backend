package com.salonplatform.notification;

import com.salonplatform.config.DemoSandboxProperties;
import com.salonplatform.domain.entity.Tenant;
import com.salonplatform.domain.repository.TenantRepository;
import com.salonplatform.util.PhoneUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * Decides whether a customer message for a brand goes to MSG91 or is only recorded. Brands in
 * SIMULATE mode never reach the provider unless the recipient is an allow-listed team phone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboundMessagingGate {

    static final String SIMULATED_ID_PREFIX = "simulated-";

    private final TenantRepository tenantRepository;
    private final DemoSandboxProperties sandboxProperties;

    public boolean shouldSimulate(UUID tenantId, String normalizedPhone) {
        if (tenantId == null) {
            return false;
        }
        boolean simulated = tenantRepository.findById(tenantId).map(Tenant::isMessagingSimulated).orElse(false);
        return simulated && !isAllowListed(normalizedPhone);
    }

    public Msg91Client.Msg91SendResult simulatedResult(UUID tenantId, String templateOrFlow) {
        log.info("Simulated outbound message tenant={} template={}", tenantId, templateOrFlow);
        return Msg91Client.Msg91SendResult.simulated(SIMULATED_ID_PREFIX + UUID.randomUUID());
    }

    private boolean isAllowListed(String normalizedPhone) {
        if (normalizedPhone == null || normalizedPhone.isBlank()) {
            return false;
        }
        return sandboxProperties.getAllowedPhones().stream()
                .map(PhoneUtils::normalizeIndianMobile)
                .filter(Objects::nonNull)
                .anyMatch(normalizedPhone::equals);
    }
}
