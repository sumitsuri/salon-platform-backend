package com.salonplatform.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.salonplatform.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
@Slf4j
public class GooglePlacesPhotoService {

    private static final String BASE = "https://places.googleapis.com/v1";

    private final GooglePlacesProperties properties;

    public ResponseEntity<byte[]> fetchPhoto(String photoName, int maxHeightPx) {
        if (!properties.isConfigured()) {
            throw new BadRequestException("Google Places API is not configured.");
        }
        if (photoName == null || photoName.isBlank()) {
            throw new BadRequestException("Photo reference is required.");
        }
        String resource = photoName.startsWith("places/") ? photoName : photoName.trim();
        int height = Math.min(Math.max(maxHeightPx, 64), 800);
        String mediaUrl = BASE + "/" + resource + "/media?maxHeightPx=" + height + "&skipHttpRedirect=true";

        RestClient google = RestClient.builder()
                .defaultHeader("X-Goog-Api-Key", properties.getApiKey())
                .build();

        try {
            JsonNode meta = google.get().uri(mediaUrl).retrieve().body(JsonNode.class);
            if (meta == null) {
                throw new BadRequestException("Could not load place photo.");
            }
            String photoUri = meta.path("photoUri").asText(null);
            if (photoUri == null || photoUri.isBlank()) {
                log.warn("Places photo media missing photoUri for {}", resource);
                throw new BadRequestException("Could not load place photo.");
            }

            return RestClient.create()
                    .get()
                    .uri(photoUri)
                    .exchange((req, res) -> {
                        byte[] body = res.getBody().readAllBytes();
                        MediaType type = res.getHeaders().getContentType();
                        return ResponseEntity.ok()
                                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                                .contentType(type != null ? type : MediaType.IMAGE_JPEG)
                                .body(body);
                    });
        } catch (RestClientResponseException e) {
            log.warn("Places photo fetch failed {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BadRequestException("Could not load place photo: " + e.getStatusCode().value());
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Places photo fetch failed: {}", e.getMessage());
            throw new BadRequestException("Could not load place photo.");
        }
    }
}
