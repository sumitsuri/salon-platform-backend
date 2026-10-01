package com.salonplatform.sales.infrastructure;

import com.salonplatform.domain.enums.TenantStatus;
import com.salonplatform.domain.repository.TenantRepository;
import com.salonplatform.sales.domain.port.TenantReadPort;
import com.salonplatform.sales.domain.port.TenantSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class MonolithTenantReadAdapter implements TenantReadPort {

    private final TenantRepository tenantRepository;

    @Override
    public TenantSnapshot getSnapshot() {
        // Demo brands are sales showcases, not customers.
        long active = tenantRepository.findByStatus(TenantStatus.ACTIVE).stream().filter(t -> !t.isDemo()).count();
        long trial = tenantRepository.findByStatus(TenantStatus.TRIAL).stream().filter(t -> !t.isDemo()).count();
        long total = tenantRepository.findAll().stream().filter(t -> !t.isDemo()).count();
        return new TenantSnapshot(active, trial, total, Instant.now());
    }
}
