package com.salonplatform.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salonplatform.config.Msg91Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class Msg91Client {

    private static final String WHATSAPP_URL = "https://api.msg91.com/api/v5/whatsapp/whatsapp-outbound-message/";
    private static final String SMS_FLOW_URL = "https://control.msg91.com/api/v5/flow/";

    private final RestTemplate restTemplate;
    private final Msg91Properties properties;
    private final ObjectMapper objectMapper;

    public Msg91SendResult sendWhatsAppTemplate(
            String toPhone,
            String templateName,
            List<Map<String, Object>> components) {
        if (!properties.isEnabled()) {
            return Msg91SendResult.skipped("MSG91 not configured");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("integrated_number", properties.getWhatsappIntegratedNumber());
        body.put("content_type", "template");

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", templateName);
        template.put("language", Map.of("code", "en", "policy", "deterministic"));
        if (components != null && !components.isEmpty()) {
            template.put("components", components);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", toPhone);
        payload.put("type", "template");
        payload.put("template", template);
        body.put("payload", payload);

        return post(WHATSAPP_URL, body);
    }

    public Msg91SendResult sendSmsFlow(String toPhone, Map<String, String> variables) {
        if (!properties.isEnabled()) {
            return Msg91SendResult.skipped("MSG91 not configured");
        }
        if (properties.getPromoSmsFlowId() == null || properties.getPromoSmsFlowId().isBlank()) {
            return Msg91SendResult.skipped("SMS flow id not configured");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("flow_id", properties.getPromoSmsFlowId());
        body.put("sender", properties.getSmsSender());
        body.put("mobiles", toPhone);
        if (variables != null) {
            body.putAll(variables);
        }

        return post(SMS_FLOW_URL, body);
    }

    private Msg91SendResult post(String url, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authkey", properties.getAuthKey());

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class);

            String responseBody = response.getBody();
            String messageId = extractMessageId(responseBody);
            if (response.getStatusCode().is2xxSuccessful()) {
                return Msg91SendResult.sent(messageId, responseBody);
            }
            return Msg91SendResult.failed("HTTP " + response.getStatusCode().value() + ": " + responseBody);
        } catch (RestClientException ex) {
            log.warn("MSG91 request failed: {}", ex.getMessage());
            return Msg91SendResult.failed(ex.getMessage());
        }
    }

    private String extractMessageId(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            if (node.has("message_uuid")) {
                return node.get("message_uuid").asText();
            }
            if (node.has("request_id")) {
                return node.get("request_id").asText();
            }
            if (node.has("type") && node.get("type").asText().equals("success")) {
                return node.path("message").asText(null);
            }
        } catch (Exception ignored) {
            // best-effort parse
        }
        return null;
    }

    public record Msg91SendResult(boolean success, boolean skipped, String messageId, String error) {
        static Msg91SendResult sent(String messageId, String raw) {
            return new Msg91SendResult(true, false, messageId, null);
        }

        static Msg91SendResult failed(String error) {
            return new Msg91SendResult(false, false, null, error);
        }

        static Msg91SendResult skipped(String reason) {
            return new Msg91SendResult(false, true, null, reason);
        }
    }
}
