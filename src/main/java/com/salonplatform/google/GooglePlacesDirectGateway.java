package com.salonplatform.google;

import com.fasterxml.jackson.databind.JsonNode;
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
 * Direct calls to Google Places API (New). Used on production and by the internal proxy controller.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GooglePlacesDirectGateway {

    private static final String BASE = "https://places.googleapis.com/v1";
    private static final String FIELD_MASK =
            "places.id,places.displayName,places.formattedAddress,places.location,places.rating,"
                    + "places.userRatingCount,places.googleMapsUri,places.websiteUri,places.nationalPhoneNumber,"
                    + "places.regularOpeningHours,places.currentOpeningHours,places.photos,places.types,places.primaryType";
    /** Text Search pagination token is a top-level response field. */
    private static final String TEXT_SEARCH_FIELD_MASK = FIELD_MASK + ",nextPageToken";
    private static final long TEXT_SEARCH_PAGE_DELAY_MS = 2_000L;
    private static final String PLACE_FIELD_MASK =
            "id,displayName,formattedAddress,location,rating,userRatingCount,googleMapsUri,websiteUri,"
                    + "nationalPhoneNumber,regularOpeningHours,currentOpeningHours,photos,types,primaryType";
    private static final String PLACE_REVIEWS_FIELD_MASK =
            "id,reviews.rating,reviews.text";

    private final GooglePlacesProperties properties;

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
        JsonNode root = post("/places:searchText", body, TEXT_SEARCH_FIELD_MASK);
        return parsePlaces(root.path("places"));
    }

    /**
     * Text Search supports pagination ({@code nextPageToken}); Nearby Search (New) does not — max 20 per call.
     */
    public List<GooglePlaceSnapshot> searchTextAllPages(
            String textQuery, Double lat, Double lng, int radiusMeters, int maxPages) {
        int pages = Math.max(1, Math.min(maxPages, 10));
        List<GooglePlaceSnapshot> all = new ArrayList<>();
        String pageToken = null;
        for (int page = 0; page < pages; page++) {
            if (page > 0 && pageToken != null) {
                sleepBeforeTextSearchPage();
            }
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("textQuery", textQuery);
            body.put("pageSize", 20);
            body.put("languageCode", "en");
            body.put("regionCode", "IN");
            if (pageToken != null && !pageToken.isBlank()) {
                body.put("pageToken", pageToken);
            }
            if (lat != null && lng != null) {
                body.put("locationBias", Map.of(
                        "circle", Map.of(
                                "center", Map.of("latitude", lat, "longitude", lng),
                                "radius", (double) radiusMeters)));
            }
            JsonNode root = post("/places:searchText", body, TEXT_SEARCH_FIELD_MASK);
            all.addAll(parsePlaces(root.path("places")));
            JsonNode next = root.path("nextPageToken");
            pageToken = next.isMissingNode() || next.isNull() ? null : next.asText(null);
            if (pageToken == null || pageToken.isBlank()) {
                break;
            }
        }
        return all;
    }

    private static void sleepBeforeTextSearchPage() {
        try {
            Thread.sleep(TEXT_SEARCH_PAGE_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

    public GooglePlacesClient.TextSearchInsight analyzeTextSearch(
            String keyword, String placeId, double lat, double lng, int radiusMeters) {
        List<GooglePlaceSnapshot> results = searchText(keyword, lat, lng, radiusMeters, 20);
        List<GoogleRankedPlace> topPlaces = new ArrayList<>();
        int rank = -1;
        String target = placeId != null && !placeId.isBlank() ? GooglePlacesClient.normalizePlaceId(placeId) : "";
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
            if (!target.isBlank() && target.equals(GooglePlacesClient.normalizePlaceId(result.getPlaceId()))) {
                rank = i + 1;
            }
        }
        return new GooglePlacesClient.TextSearchInsight(rank, topPlaces);
    }

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
            throw new BadRequestException(formatGooglePlacesError(e));
        }
    }

    static String formatGooglePlacesError(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body != null && body.contains("API_KEY_IP_ADDRESS_BLOCKED")) {
            return "Google Places API key is restricted to server IPs (403). For local dev: set "
                    + "GOOGLE_PLACES_API_KEY_LOCAL to an unrestricted dev key, enable prod proxy "
                    + "(APP_GOOGLE_PLACES_INTERNAL_PROXY_ENABLED=true), or add your public IP in Google Cloud "
                    + "Console → APIs & Services → Credentials → your API key → IP restrictions. "
                    + "See backend/docs/GOOGLE_PLACES_LOCAL.md.";
        }
        if (body != null && body.contains("API_KEY_HTTP_REFERRER_BLOCKED")) {
            return "Google Places API key is restricted by HTTP referrer (403). Use a server key without "
                    + "referrer restrictions for backend Places calls.";
        }
        return "Google Places API request failed: " + e.getStatusCode().value();
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
        String photoName = null;
        if (p.path("photos").isArray() && !p.path("photos").isEmpty()) {
            photoName = text(p.path("photos").get(0), "name");
        }
        JsonNode currentHours = p.path("currentOpeningHours");
        JsonNode regularHours = p.path("regularOpeningHours");
        Boolean openNow = null;
        if (currentHours.has("openNow") && currentHours.path("openNow").isBoolean()) {
            openNow = currentHours.path("openNow").asBoolean();
        } else if (regularHours.has("openNow") && regularHours.path("openNow").isBoolean()) {
            openNow = regularHours.path("openNow").asBoolean();
        }
        String hoursSummary = summarizeHours(regularHours, currentHours, openNow);
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
                .photoName(photoName)
                .openNow(openNow)
                .hoursSummary(hoursSummary)
                .primaryType(text(p, "primaryType"))
                .build();
    }

    private static String summarizeHours(JsonNode regularHours, JsonNode currentHours, Boolean openNow) {
        if (currentHours.has("nextCloseTime") && !currentHours.path("nextCloseTime").isNull()) {
            String close = currentHours.path("nextCloseTime").asText(null);
            if (close != null && Boolean.TRUE.equals(openNow)) {
                return "Open now · closes " + formatTimeHint(close);
            }
        }
        if (currentHours.has("nextOpenTime") && !currentHours.path("nextOpenTime").isNull()) {
            String open = currentHours.path("nextOpenTime").asText(null);
            if (open != null && Boolean.FALSE.equals(openNow)) {
                return "Closed · opens " + formatTimeHint(open);
            }
        }
        if (regularHours.path("weekdayDescriptions").isArray()
                && !regularHours.path("weekdayDescriptions").isEmpty()) {
            java.time.DayOfWeek today = java.time.LocalDate.now().getDayOfWeek();
            int idx = today.getValue() - 1;
            JsonNode descriptions = regularHours.path("weekdayDescriptions");
            if (idx >= 0 && idx < descriptions.size()) {
                return descriptions.get(idx).asText(null);
            }
            return descriptions.get(0).asText(null);
        }
        if (openNow != null) {
            return openNow ? "Open now" : "Closed now";
        }
        return null;
    }

    /** Best-effort friendly time from ISO-ish timestamp in Places response. */
    private static String formatTimeHint(String iso) {
        try {
            return java.time.ZonedDateTime.parse(iso)
                    .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"));
        } catch (Exception ignored) {
            return iso.length() > 16 ? iso.substring(11, 16) : iso;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText(null);
    }
}
