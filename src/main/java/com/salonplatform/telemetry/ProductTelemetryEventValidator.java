package com.salonplatform.telemetry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salonplatform.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class ProductTelemetryEventValidator {

    private static final Set<String> ALLOWED_EVENT_NAMES = Set.of(
            "screen_viewed",
            "session_started",
            "feature_used",
            "login_succeeded");

    private static final Set<String> ALLOWED_PORTALS = Set.of("employee", "manager", "admin", "platform");

    private static final Set<String> ALLOWED_PROPERTY_KEYS = Set.of(
            "source",
            "action",
            "duration_ms");

    private static final int MAX_FEATURE_KEY_LEN = 128;
    private static final int MAX_ROUTE_LEN = 256;
    private static final int MAX_PROPERTIES_JSON_LEN = 2048;

    private final ObjectMapper objectMapper;

    public ProductTelemetryEventValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ValidatedClientEvent validate(IngestTelemetryEventRequest.ClientEvent raw) {
        if (raw.getEventName() == null || raw.getEventName().isBlank()) {
            throw new BadRequestException("Telemetry event name required");
        }
        String eventName = raw.getEventName().trim();
        if (!ALLOWED_EVENT_NAMES.contains(eventName)) {
            throw new BadRequestException("Telemetry event name not allowed");
        }

        Instant occurredAt;
        try {
            occurredAt = raw.getOccurredAt() == null || raw.getOccurredAt().isBlank()
                    ? Instant.now()
                    : Instant.parse(raw.getOccurredAt());
        } catch (Exception e) {
            throw new BadRequestException("Invalid occurredAt");
        }

        UUID sessionId = parseUuid(raw.getSessionId(), "sessionId");

        String portal = normalizePortal(raw.getPortal());
        String featureKey = trimToMax(raw.getFeatureKey(), MAX_FEATURE_KEY_LEN);
        String route = trimToMax(raw.getRoute(), MAX_ROUTE_LEN);
        String client = raw.getClient() == null || raw.getClient().isBlank() ? "web" : raw.getClient().trim();
        if (client.length() > 16) {
            client = client.substring(0, 16);
        }
        String appVersion = trimToMax(raw.getAppVersion(), 64);
        UUID clientEventId = raw.getClientEventId() != null && !raw.getClientEventId().isBlank()
                ? parseUuid(raw.getClientEventId(), "clientEventId")
                : null;

        String propertiesJson = sanitizeProperties(raw.getProperties());

        return new ValidatedClientEvent(
                eventName,
                occurredAt,
                sessionId,
                portal,
                featureKey,
                route,
                propertiesJson,
                client,
                appVersion,
                clientEventId);
    }

    private String normalizePortal(String portal) {
        if (portal == null || portal.isBlank()) {
            return null;
        }
        String p = portal.trim().toLowerCase();
        if (!ALLOWED_PORTALS.contains(p)) {
            throw new BadRequestException("Invalid telemetry portal");
        }
        return p;
    }

    private String sanitizeProperties(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return null;
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getKey() == null || !ALLOWED_PROPERTY_KEYS.contains(entry.getKey())) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof String s) {
                if (s.length() > 256) {
                    safe.put(entry.getKey(), s.substring(0, 256));
                } else {
                    safe.put(entry.getKey(), s);
                }
            } else if (value instanceof Number || value instanceof Boolean) {
                safe.put(entry.getKey(), value);
            }
        }
        if (safe.isEmpty()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(safe);
            if (json.length() > MAX_PROPERTIES_JSON_LEN) {
                return null;
            }
            return json;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(value.trim());
        } catch (Exception e) {
            throw new BadRequestException("Invalid " + field);
        }
    }

    private static String trimToMax(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    public record ValidatedClientEvent(
            String eventName,
            Instant occurredAt,
            UUID sessionId,
            String portal,
            String featureKey,
            String route,
            String propertiesJson,
            String client,
            String appVersion,
            UUID clientEventId) {}
}
