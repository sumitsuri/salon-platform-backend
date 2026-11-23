package com.salonplatform.aigateway.ledger;

import java.time.Instant;

/**
 * Where the gateway records what happened and reads back what has been used. The module ships an in-memory version;
 * an application supplies a durable one (a database, Redis), and a standalone gateway service would own its own store.
 */
public interface GatewayLedger {

    void record(AuditEvent event);

    /**
     * @param dayStart         the start of the current day (in the business's time zone)
     * @param monthStart       the start of the current month
     * @param violationsSince  the start of the window in which blocked attempts are counted
     */
    UsageSnapshot snapshot(String tenantId, String userId, Instant dayStart, Instant monthStart, Instant violationsSince);
}
