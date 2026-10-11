package com.salonplatform.telemetry;

import com.salonplatform.config.ProductTelemetryProperties;
import com.salonplatform.domain.enums.UserRole;
import com.salonplatform.security.UserPrincipal;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductTelemetryIngestService {

    private static final String INSERT_EVENT_SQL = """
            INSERT INTO product_events (
                id, occurred_at, event_name, event_version,
                tenant_id, branch_id, user_id, role,
                portal, session_id, feature_key, route,
                properties, client, app_version, client_event_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
            """;

    private final ProductTelemetryProperties properties;
    private final ProductTelemetryEventValidator validator;
    private final JdbcTemplate jdbcTemplate;
    private final ProductUsageRollupService rollupService;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private BlockingQueue<StoredEvent> queue;

    public void startFlusher() {
        int capacity = Math.max(1_000, properties.getQueueCapacity());
        queue = new ArrayBlockingQueue<>(capacity);
        Thread flusher = new Thread(this::flushLoop, "product-telemetry-flusher");
        flusher.setDaemon(true);
        flusher.start();
    }

    @PreDestroy
    void shutdown() {
        running.set(false);
        flushOnce();
    }

    public void acceptFromClient(UserPrincipal user, IngestTelemetryEventRequest request) {
        if (!properties.isEnabled() || request.getEvents() == null || request.getEvents().isEmpty()) {
            return;
        }
        int limit = Math.min(request.getEvents().size(), properties.getMaxBatchSize());
        for (int i = 0; i < limit; i++) {
            IngestTelemetryEventRequest.ClientEvent raw = request.getEvents().get(i);
            try {
                ProductTelemetryEventValidator.ValidatedClientEvent validated = validator.validate(raw);
                enqueue(buildStoredEvent(user, validated));
            } catch (Exception e) {
                log.debug("Skipping invalid telemetry event: {}", e.getMessage());
            }
        }
    }

    public void recordLoginSucceeded(UserPrincipal user, String portal) {
        if (!properties.isEnabled() || user == null) {
            return;
        }
        String normalizedPortal = portal == null || portal.isBlank() ? null : portal.trim().toLowerCase();
        ProductTelemetryEventValidator.ValidatedClientEvent validated = new ProductTelemetryEventValidator.ValidatedClientEvent(
                "login_succeeded",
                Instant.now(),
                null,
                normalizedPortal,
                "auth.login",
                null,
                null,
                "server",
                null,
                UUID.randomUUID());
        StoredEvent event = buildStoredEvent(user, validated);
        if (!persistEventBatch(List.of(event))) {
            enqueue(event);
            return;
        }
        try {
            upsertSnapshots(List.of(event));
        } catch (Exception e) {
            log.warn("Product telemetry snapshot upsert failed for login: {}", e.getMessage());
        }
        rollupAffectedDays(List.of(event));
    }

    private StoredEvent buildStoredEvent(UserPrincipal user, ProductTelemetryEventValidator.ValidatedClientEvent validated) {
        return new StoredEvent(
                UUID.randomUUID(),
                validated.occurredAt(),
                validated.eventName(),
                (short) 1,
                user.getTenantId(),
                user.getBranchId(),
                user.getId(),
                user.getRole() != null ? user.getRole().name() : UserRole.SALON_STAFF.name(),
                validated.portal(),
                validated.sessionId(),
                validated.featureKey(),
                validated.route(),
                validated.propertiesJson(),
                validated.client(),
                validated.appVersion(),
                validated.clientEventId());
    }

    private void enqueue(StoredEvent event) {
        if (queue == null) {
            log.warn("Product telemetry queue not started; dropping {} event", event.eventName());
            return;
        }
        if (!queue.offer(event)) {
            StoredEvent dropped = queue.poll();
            if (dropped != null) {
                log.warn("Product telemetry queue full; dropped oldest event {}", dropped.eventName());
            }
            queue.offer(event);
        }
    }

    private void flushLoop() {
        while (running.get()) {
            try {
                Thread.sleep(Math.max(250L, properties.getFlushIntervalMs()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            flushOnce();
        }
    }

    void flushOnce() {
        if (queue == null || queue.isEmpty()) {
            return;
        }
        List<StoredEvent> batch = new ArrayList<>();
        queue.drainTo(batch, 500);
        if (batch.isEmpty()) {
            return;
        }
        if (!persistEventBatch(batch)) {
            requeue(batch);
            return;
        }

        try {
            upsertSnapshots(batch);
        } catch (Exception e) {
            log.warn("Product telemetry snapshot upsert failed ({} events): {}", batch.size(), e.getMessage());
        }

        rollupAffectedDays(batch);
    }

    /** @return true when all rows were inserted */
    private boolean persistEventBatch(List<StoredEvent> batch) {
        if (batch.isEmpty()) {
            return true;
        }
        try {
            jdbcTemplate.batchUpdate(
                    INSERT_EVENT_SQL,
                    batch,
                    batch.size(),
                    (ps, event) -> bindEvent(ps, event));
            return true;
        } catch (Exception e) {
            log.error("Product telemetry insert failed ({} events): {}", batch.size(), e.getMessage());
            return false;
        }
    }

    private void bindEvent(java.sql.PreparedStatement ps, StoredEvent event) throws java.sql.SQLException {
        ps.setObject(1, event.id());
        ps.setTimestamp(2, Timestamp.from(event.occurredAt()));
        ps.setString(3, event.eventName());
        ps.setShort(4, event.eventVersion());
        ps.setObject(5, event.tenantId());
        ps.setObject(6, event.branchId());
        ps.setObject(7, event.userId());
        ps.setString(8, event.role());
        ps.setString(9, event.portal());
        ps.setObject(10, event.sessionId());
        ps.setString(11, event.featureKey());
        ps.setString(12, event.route());
        if (event.propertiesJson() == null) {
            ps.setNull(13, java.sql.Types.OTHER);
        } else {
            ps.setString(13, event.propertiesJson());
        }
        ps.setString(14, event.client());
        ps.setString(15, event.appVersion());
        ps.setObject(16, event.clientEventId());
    }

    private void requeue(List<StoredEvent> batch) {
        if (queue == null) {
            return;
        }
        for (StoredEvent event : batch) {
            if (!queue.offer(event)) {
                log.warn("Product telemetry requeue full; dropping {}", event.eventName());
                break;
            }
        }
    }

    private void rollupAffectedDays(List<StoredEvent> batch) {
        if (!properties.isEnabled()) {
            return;
        }
        Set<LocalDate> days = new HashSet<>();
        for (StoredEvent event : batch) {
            days.add(event.occurredAt().atZone(ZoneOffset.UTC).toLocalDate());
        }
        for (LocalDate day : days) {
            try {
                rollupService.rollupDay(day);
            } catch (Exception e) {
                log.warn("Product usage rollup failed for {}: {}", day, e.getMessage());
            }
        }
    }

    private void upsertSnapshots(List<StoredEvent> batch) {
        for (StoredEvent event : batch) {
            if (event.tenantId() == null || event.userId() == null) {
                continue;
            }
            int loginDelta = "login_succeeded".equals(event.eventName()) ? 1 : 0;
            jdbcTemplate.update("""
                            INSERT INTO user_usage_snapshot (
                                tenant_id, user_id, last_seen_at, last_portal,
                                last_feature_key, last_route, first_seen_at, login_count, days_active_28d
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                            ON CONFLICT (tenant_id, user_id) DO UPDATE SET
                                last_seen_at = GREATEST(user_usage_snapshot.last_seen_at, EXCLUDED.last_seen_at),
                                last_portal = COALESCE(EXCLUDED.last_portal, user_usage_snapshot.last_portal),
                                last_feature_key = COALESCE(EXCLUDED.last_feature_key, user_usage_snapshot.last_feature_key),
                                last_route = COALESCE(EXCLUDED.last_route, user_usage_snapshot.last_route),
                                first_seen_at = LEAST(user_usage_snapshot.first_seen_at, EXCLUDED.first_seen_at),
                                login_count = user_usage_snapshot.login_count + ?
                            """,
                    event.tenantId(),
                    event.userId(),
                    Timestamp.from(event.occurredAt()),
                    event.portal(),
                    event.featureKey(),
                    event.route(),
                    Timestamp.from(event.occurredAt()),
                    loginDelta,
                    loginDelta);
        }
    }

    private record StoredEvent(
            UUID id,
            Instant occurredAt,
            String eventName,
            short eventVersion,
            UUID tenantId,
            UUID branchId,
            UUID userId,
            String role,
            String portal,
            UUID sessionId,
            String featureKey,
            String route,
            String propertiesJson,
            String client,
            String appVersion,
            UUID clientEventId) {}
}
