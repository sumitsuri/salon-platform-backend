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
        long active = tenantRepository.findByStatus(TenantStatus.ACTIVE).size();
        long trial = tenantRepository.findByStatus(TenantStatus.TRIAL).size();
        long total = tenantRepository.count();
        return new TenantSnapshot(active, trial, total, Instant.now());
    }
}
