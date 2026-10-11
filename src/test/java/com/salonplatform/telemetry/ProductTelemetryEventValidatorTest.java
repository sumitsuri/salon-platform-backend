package com.salonplatform.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salonplatform.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductTelemetryEventValidatorTest {

    private final ProductTelemetryEventValidator validator =
            new ProductTelemetryEventValidator(new ObjectMapper());

    @Test
    void acceptsScreenViewedWithFeatureKey() {
        IngestTelemetryEventRequest.ClientEvent raw = new IngestTelemetryEventRequest.ClientEvent();
        raw.setEventName("screen_viewed");
        raw.setSessionId(UUID.randomUUID().toString());
        raw.setPortal("manager");
        raw.setFeatureKey("manager.home");
        raw.setRoute("/manager");

        var validated = validator.validate(raw);
        assertEquals("screen_viewed", validated.eventName());
        assertEquals("manager.home", validated.featureKey());
    }

    @Test
    void rejectsUnknownEventName() {
        IngestTelemetryEventRequest.ClientEvent raw = new IngestTelemetryEventRequest.ClientEvent();
        raw.setEventName("custom_hack");
        assertThrows(BadRequestException.class, () -> validator.validate(raw));
    }

    @Test
    void stripsDisallowedPropertyKeys() {
        IngestTelemetryEventRequest.ClientEvent raw = new IngestTelemetryEventRequest.ClientEvent();
        raw.setEventName("feature_used");
        raw.setSessionId(UUID.randomUUID().toString());
        raw.setPortal("employee");
        raw.setProperties(Map.of("email", "secret@example.com", "source", "test"));

        var validated = validator.validate(raw);
        assertEquals("{\"source\":\"test\"}", validated.propertiesJson());
    }
}
