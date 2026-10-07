package com.salonplatform.service.scan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salonplatform.config.ScanLlmProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanVisionLlmService {

    private final ScanLlmProperties properties;
    private final ObjectMapper objectMapper;

    public Optional<JsonNode> analyzeScalp(
            List<byte[]> jpegImages,
            List<String> zoneLabels,
            Set<String> staffConcerns,
            String staffNotes,
            List<String> catalogServiceNames) {
        return analyze("scalp and hair", scalpConcernCodes(), jpegImages, zoneLabels, staffConcerns, staffNotes, catalogServiceNames);
    }

    public Optional<JsonNode> analyzeFace(
            List<byte[]> jpegImages,
            List<String> zoneLabels,
            Set<String> staffConcerns,
            String staffNotes,
            List<String> catalogServiceNames) {
        return analyze("facial skin", faceConcernCodes(), jpegImages, zoneLabels, staffConcerns, staffNotes, catalogServiceNames);
    }

    private Optional<JsonNode> analyze(
            String domain,
            String concernCodes,
            List<byte[]> jpegImages,
            List<String> zoneLabels,
            Set<String> staffConcerns,
            String staffNotes,
            List<String> catalogServiceNames) {
        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return Optional.empty();
        }
        int limit = Math.min(properties.getMaxImages(), jpegImages.size());
        if (limit == 0) {
            return Optional.empty();
        }

        try {
            List<Map<String, Object>> content = new ArrayList<>();
            content.add(Map.of(
                    "type", "text",
                    "text", prompt(domain, concernCodes, zoneLabels, staffConcerns, staffNotes, catalogServiceNames)));

            for (int i = 0; i < limit; i++) {
                String b64 = Base64.getEncoder().encodeToString(downscale(jpegImages.get(i), 768));
                content.add(Map.of(
                        "type", "image_url",
                        "image_url", Map.of("url", "data:image/jpeg;base64," + b64, "detail", "low")));
            }

            Map<String, Object> body = Map.of(
                    "model", properties.getModel(),
                    "temperature", 0.3,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(Map.of("role", "user", "content", content)));

            RestClient client = RestClient.builder()
                    .baseUrl(properties.getBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                    .build();

            String responseBody = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            String jsonText = root.path("choices").path(0).path("message").path("content").asText(null);
            if (jsonText == null || jsonText.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readTree(jsonText));
        } catch (Exception e) {
            log.warn("Scan LLM analysis failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String prompt(
            String domain,
            String concernCodes,
            List<String> zoneLabels,
            Set<String> staffConcerns,
            String staffNotes,
            List<String> catalog) {
        return """
                You assist salon stylists (not doctors). Analyze %s photos for consultation only.
                Zones captured: %s
                Stylist-confirmed concerns (PRIORITIZE these): %s
                Stylist notes: %s
                Branch services (ONLY recommend from this list by exact or close name): %s
                Valid concern codes: %s

                Return JSON only:
                {
                  "concerns": [{"code":"...","label":"...","severity":"LOW|MEDIUM|HIGH","insight":"one short factual line"}],
                  "inSalonServices": [{"name":"from catalog","reason":"why this helps this guest"}],
                  "routineSteps": [{"step":1,"phase":"CLEANSE|TREAT|MOISTURIZE|PROTECT","title":"...","description":"..."}],
                  "carePlanPhases": [{"month":1,"title":"...","goal":"...","rationale":"...","inSalonVisit":{"name":"...","reason":"..."},"homeRoutine":[{"step":1,"phase":"...","title":"...","description":"..."}]}]
                }
                Rules: max 3 concerns; max 2 inSalonServices for scalp, for face use carePlanPhases with 3 months each with ONE inSalonVisit; no medical diagnosis; if unsure, lower severity and say what stylist should verify.
                """
                .formatted(
                        domain,
                        String.join(", ", zoneLabels),
                        staffConcerns.isEmpty() ? "none — rely on images but stay cautious" : String.join(", ", staffConcerns),
                        staffNotes == null || staffNotes.isBlank() ? "none" : staffNotes,
                        catalog.isEmpty() ? "none" : String.join("; ", catalog.subList(0, Math.min(40, catalog.size()))),
                        concernCodes);
    }

    private static String scalpConcernCodes() {
        return "DANDRUFF,OILY_SCALP,DRY_SCALP,SCALP_IRRITATION,HAIR_THINNING,HAIR_BREAKAGE,PRODUCT_BUILDUP,BALANCED";
    }

    private static String faceConcernCodes() {
        return "OILY_SKIN,DRY_DEHYDRATED,REDNESS_SENSITIVITY,UNEVEN_TEXTURE,DULLNESS,ACNE_BLEMISH,COMBINATION_SKIN,BALANCED";
    }

    private static byte[] downscale(byte[] input, int maxSide) throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(input));
        if (img == null) {
            return input;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        double scale = Math.min(1.0, (double) maxSide / Math.max(w, h));
        if (scale >= 1.0) {
            return input;
        }
        int nw = (int) Math.round(w * scale);
        int nh = (int) Math.round(h * scale);
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, nw, nh, null);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(out, "jpg", bos);
        return bos.toByteArray();
    }
}
