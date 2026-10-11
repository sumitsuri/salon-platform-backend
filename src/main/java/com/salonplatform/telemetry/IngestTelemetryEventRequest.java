package com.salonplatform.telemetry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class IngestTelemetryEventRequest {

    @NotEmpty
    @Size(max = 50)
    @Valid
    private List<ClientEvent> events;

    @Data
    public static class ClientEvent {
        private String clientEventId;
        @NotEmpty
        private String eventName;
        private String occurredAt;
        private String sessionId;
        private String portal;
        private String featureKey;
        private String route;
        private Map<String, Object> properties;
        private String client;
        private String appVersion;
    }
}
