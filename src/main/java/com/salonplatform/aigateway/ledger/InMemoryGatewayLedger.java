package com.salonplatform.aigateway.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A process-local ledger: fine for tests and a single instance, lost on restart. */
public final class InMemoryGatewayLedger implements GatewayLedger {

    private final List<AuditEvent> events = new ArrayList<>();

    @Override
    public synchronized void record(AuditEvent event) {
        events.add(event);
    }

    public synchronized List<AuditEvent> events() {
        return List.copyOf(events);
    }

    @Override
    public synchronized UsageSnapshot snapshot(String tenantId, String userId, Instant dayStart, Instant monthStart,
                                               Instant violationsSince) {
        int userRequests = 0, tenantRequests = 0, violations = 0;
        BigDecimal day = BigDecimal.ZERO, month = BigDecimal.ZERO;
        Instant last = null;
        for (AuditEvent e : events) {
            if (!Objects.equals(e.tenantId(), tenantId)) continue;
            boolean isToday = !e.at().isBefore(dayStart);
            if (AuditEvent.REQUEST.equals(e.kind()) && isToday) {
                tenantRequests++;
                if (Objects.equals(e.userId(), userId)) userRequests++;
            }
            BigDecimal cost = e.costInr() == null ? BigDecimal.ZERO : e.costInr();
            if (!e.at().isBefore(monthStart)) month = month.add(cost);
            if (isToday) day = day.add(cost);
            if (AuditEvent.BLOCK.equals(e.kind()) && Objects.equals(e.userId(), userId) && !e.at().isBefore(violationsSince)) {
                violations++;
                if (last == null || e.at().isAfter(last)) last = e.at();
            }
        }
        return new UsageSnapshot(userRequests, tenantRequests, day, month, violations, last);
    }
}
