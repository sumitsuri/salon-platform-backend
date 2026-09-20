package com.salonplatform.google;

import com.salonplatform.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * For local dev: route Places calls through production API (IP-allowed) instead of calling Google directly.
 */
@Component
@RequiredArgsConstructor
public class GooglePlacesProdProxy {

    private static final String SECRET_HEADER = "X-Places-Proxy-Secret";

    private final GooglePlacesProperties properties;

    public List<GooglePlaceSnapshot> searchNearby(
            double lat, double lng, int radiusMeters, int maxResults, List<String> includedTypes) {
        NearbyProxyRequest req = new NearbyProxyRequest(lat, lng, radiusMeters, maxResults, includedTypes);
        return post("/search-nearby", req, new ParameterizedTypeReference<>() {});
    }

    public List<GooglePlaceSnapshot> searchText(
            String textQuery, Double lat, Double lng, int radiusMeters, int maxResults) {
        TextProxyRequest req = new TextProxyRequest(textQuery, lat, lng, radiusMeters, maxResults);
        return post("/search-text", req, new ParameterizedTypeReference<>() {});
    }

    private <T> List<GooglePlaceSnapshot> post(String path, T body, ParameterizedTypeReference<List<GooglePlaceSnapshot>> type) {
        if (!properties.isInternalProxyConfigured()) {
            throw new BadRequestException(
                    "Google Places prod proxy is not configured. Set APP_GOOGLE_PLACES_INTERNAL_PROXY_BASE_URL "
                            + "and PLACES_INTERNAL_PROXY_SECRET (see backend/docs/GOOGLE_PLACES_LOCAL.md).");
        }
        String base = properties.getInternalProxyBaseUrl().replaceAll("/+$", "");
        try {
            List<GooglePlaceSnapshot> result = RestClient.create()
                    .post()
                    .uri(base + "/api/v1/internal/places" + path)
                    .header(SECRET_HEADER, properties.getInternalProxySecret())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .retrieve()
                    .body(type);
            return result != null ? result : List.of();
        } catch (RestClientResponseException e) {
            throw new BadRequestException(
                    "Google Places prod proxy failed (" + e.getStatusCode().value() + "). "
                            + "Ensure production has PLACES_INTERNAL_PROXY_SECRET and the latest backend is deployed.");
        }
    }

    record NearbyProxyRequest(
            double lat, double lng, int radiusMeters, int maxResults, List<String> includedTypes) {}

    record TextProxyRequest(
            String textQuery, Double lat, Double lng, int radiusMeters, int maxResults) {}
}
