package com.salonplatform.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salonplatform.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Google Places API (New) — https://developers.google.com/maps/documentation/places/web-service/op-overview
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GooglePlacesClient {

    private static final String BASE = "https://places.googleapis.com/v1";
    private static final String FIELD_MASK =
            "places.id,places.displayName,places.formattedAddress,places.location,places.rating,"
                    + "places.userRatingCount,places.googleMapsUri,places.websiteUri,places.nationalPhoneNumber,"
                    + "places.regularOpeningHours,places.photos,places.types,places.primaryType";
    private static final String PLACE_FIELD_MASK =
            "id,displayName,formattedAddress,location,rating,userRatingCount,googleMapsUri,websiteUri,"
                    + "nationalPhoneNumber,regularOpeningHours,photos,types,primaryType";
    private static final String PLACE_REVIEWS_FIELD_MASK =
            "id,reviews.rating,reviews.text";

    private final GooglePlacesProperties properties;
    private final ObjectMapper objectMapper;

    private RestClient client() {
        if (!properties.isConfigured()) {
            throw new BadRequestException(
                    "Google Places API is not configured. Set GOOGLE_PLACES_API_KEY on the server.");
        }
        return RestClient.builder()
                .baseUrl(BASE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Goog-Api-Key", properties.getApiKey())
                .build();
    }

    public List<GooglePlaceSnapshot> searchText(String textQuery, Double lat, Double lng, int radiusMeters, int maxResults) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("textQuery", textQuery);
        body.put("maxResultCount", Math.min(maxResults, 20));
        body.put("languageCode", "en");
        body.put("regionCode", "IN");
        if (lat != null && lng != null) {
            body.put("locationBias", Map.of(
                    "circle", Map.of(
                            "center", Map.of("latitude", lat, "longitude", lng),
                            "radius", (double) radiusMeters)));
        }
        JsonNode root = post("/places:searchText", body, FIELD_MASK);
        return parsePlaces(root.path("places"));
    }

    public List<GooglePlaceSnapshot> searchNearby(double lat, double lng, int radiusMeters, int maxResults) {
        return searchNearby(lat, lng, radiusMeters, maxResults, List.of("hair_salon", "beauty_salon"));
    }

    public List<GooglePlaceSnapshot> searchNearby(
            double lat, double lng, int radiusMeters, int maxResults, List<String> includedTypes) {
        Map<String, Object> body = Map.of(
                "includedTypes", includedTypes,
                "maxResultCount", Math.min(maxResults, 20),
                "locationRestriction", Map.of(
                        "circle", Map.of(
                                "center", Map.of("latitude", lat, "longitude", lng),
                                "radius", (double) radiusMeters)));
        JsonNode root = post("/places:searchNearby", body, FIELD_MASK);
        return parsePlaces(root.path("places"));
    }

    public GooglePlaceSnapshot getPlace(String placeId) {
        String resource = placeId.startsWith("places/") ? placeId : "places/" + placeId;
        try {
            JsonNode node = client()
                    .get()
                    .uri("/" + resource)
                    .header("X-Goog-FieldMask", PLACE_FIELD_MASK)
                    .retrieve()
                    .body(JsonNode.class);
            if (node == null) return null;
            return parsePlace(node);
        } catch (RestClientResponseException e) {
            log.warn("Google getPlace failed for {}: {}", placeId, e.getMessage());
            return null;
        }
    }

    public int findRankInTextSearch(String keyword, String placeId, double lat, double lng, int radiusMeters) {
        return analyzeTextSearch(keyword, placeId, lat, lng, radiusMeters).rank();
    }

    public TextSearchInsight analyzeTextSearch(
            String keyword, String placeId, double lat, double lng, int radiusMeters) {
        List<GooglePlaceSnapshot> results = searchText(keyword, lat, lng, radiusMeters, 20);
        List<GoogleRankedPlace> topPlaces = new ArrayList<>();
        int rank = -1;
        String target = placeId != null && !placeId.isBlank() ? normalizePlaceId(placeId) : "";
        for (int i = 0; i < results.size(); i++) {
            GooglePlaceSnapshot result = results.get(i);
            if (i < 3 && result.getName() != null) {
                topPlaces.add(GoogleRankedPlace.builder()
                        .rank(i + 1)
                        .name(result.getName())
                        .googlePlaceId(result.getPlaceId())
                        .googleMapsUrl(result.mapsUriOrFallback())
                        .build());
            }
            if (!target.isBlank() && target.equals(normalizePlaceId(result.getPlaceId()))) {
                rank = i + 1;
            }
        }
        return new TextSearchInsight(rank, topPlaces);
    }

    /** Fetches public review sample and counts ratings below 4 stars. */
    public GooglePlaceSnapshot enrichWithReviewStats(GooglePlaceSnapshot snap) {
        if (snap == null || snap.getPlaceId() == null || snap.getPlaceId().isBlank()) {
            return snap;
        }
        String resource = snap.getPlaceId().startsWith("places/") ? snap.getPlaceId() : "places/" + snap.getPlaceId();
        try {
            JsonNode node = client()
                    .get()
                    .uri("/" + resource)
                    .header("X-Goog-FieldMask", PLACE_REVIEWS_FIELD_MASK)
                    .retrieve()
                    .body(JsonNode.class);
            if (node == null) return snap;
            JsonNode reviews = node.path("reviews");
            int sample = 0;
            int low = 0;
            if (reviews.isArray()) {
                for (JsonNode review : reviews) {
                    sample++;
                    if (review.path("rating").isNumber() && review.path("rating").asDouble() < 4.0) {
                        low++;
                    }
                }
            }
            snap.setReviewsSampleSize(sample > 0 ? sample : null);
            snap.setLowRatingReviewCount(sample > 0 ? low : null);
        } catch (RestClientResponseException e) {
            log.warn("Google review stats failed for {}: {}", snap.getPlaceId(), e.getMessage());
        }
        return snap;
    }

    public record TextSearchInsight(int rank, List<GoogleRankedPlace> topPlaces) {}

    private JsonNode post(String path, Object body, String fieldMask) {
        try {
            return client()
                    .post()
                    .uri(path)
                    .header("X-Goog-FieldMask", fieldMask)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            log.error("Google Places API error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BadRequestException("Google Places API request failed: " + e.getStatusCode().value());
        }
    }

    private List<GooglePlaceSnapshot> parsePlaces(JsonNode places) {
        List<GooglePlaceSnapshot> list = new ArrayList<>();
        if (places == null || !places.isArray()) return list;
        places.forEach(p -> {
            GooglePlaceSnapshot snap = parsePlace(p);
            if (snap != null) list.add(snap);
        });
        return list;
    }

    private GooglePlaceSnapshot parsePlace(JsonNode p) {
        if (p == null || p.isMissingNode()) return null;
        JsonNode loc = p.path("location");
        int photoCount = p.path("photos").isArray() ? p.path("photos").size() : 0;
        return GooglePlaceSnapshot.builder()
                .placeId(text(p, "id"))
                .name(text(p.path("displayName"), "text"))
                .formattedAddress(text(p, "formattedAddress"))
                .latitude(loc.path("latitude").isNumber() ? loc.path("latitude").asDouble() : null)
                .longitude(loc.path("longitude").isNumber() ? loc.path("longitude").asDouble() : null)
                .rating(p.path("rating").isNumber() ? p.path("rating").asDouble() : null)
                .reviewCount(p.path("userRatingCount").isNumber() ? p.path("userRatingCount").asInt() : null)
                .photoCount(photoCount)
                .googleMapsUri(text(p, "googleMapsUri"))
                .websiteUri(text(p, "websiteUri"))
                .phone(text(p, "nationalPhoneNumber"))
                .hasOpeningHours(p.has("regularOpeningHours") && !p.path("regularOpeningHours").isNull())
                .primaryType(text(p, "primaryType"))
                .build();
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText(null);
    }

    static String normalizePlaceId(String id) {
        if (id == null) return "";
        return id.startsWith("places/") ? id : "places/" + id;
    }
}
