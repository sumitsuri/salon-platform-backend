package com.salonplatform.google;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Google Places API (New) — https://developers.google.com/maps/documentation/places/web-service/op-overview
 */
@Component
@RequiredArgsConstructor
public class GooglePlacesClient {

    private final GooglePlacesProperties properties;
    private final GooglePlacesDirectGateway directGateway;
    private final GooglePlacesProdProxy prodProxy;

    public List<GooglePlaceSnapshot> searchText(String textQuery, Double lat, Double lng, int radiusMeters, int maxResults) {
        if (properties.useInternalProxy()) {
            return prodProxy.searchText(textQuery, lat, lng, radiusMeters, maxResults);
        }
        return directGateway.searchText(textQuery, lat, lng, radiusMeters, maxResults);
    }

    /** Paginated text search — not available through prod proxy (single page only there). */
    public List<GooglePlaceSnapshot> searchTextAllPages(
            String textQuery, Double lat, Double lng, int radiusMeters, int maxPages) {
        if (properties.useInternalProxy()) {
            return prodProxy.searchText(textQuery, lat, lng, radiusMeters, 20);
        }
        return directGateway.searchTextAllPages(textQuery, lat, lng, radiusMeters, maxPages);
    }

    public List<GooglePlaceSnapshot> searchNearby(double lat, double lng, int radiusMeters, int maxResults) {
        if (properties.useInternalProxy()) {
            return prodProxy.searchNearby(lat, lng, radiusMeters, maxResults, List.of("hair_salon", "beauty_salon"));
        }
        return directGateway.searchNearby(lat, lng, radiusMeters, maxResults);
    }

    public List<GooglePlaceSnapshot> searchNearby(
            double lat, double lng, int radiusMeters, int maxResults, List<String> includedTypes) {
        if (properties.useInternalProxy()) {
            return prodProxy.searchNearby(lat, lng, radiusMeters, maxResults, includedTypes);
        }
        return directGateway.searchNearby(lat, lng, radiusMeters, maxResults, includedTypes);
    }

    public GooglePlaceSnapshot getPlace(String placeId) {
        return directGateway.getPlace(placeId);
    }

    public int findRankInTextSearch(String keyword, String placeId, double lat, double lng, int radiusMeters) {
        return directGateway.findRankInTextSearch(keyword, placeId, lat, lng, radiusMeters);
    }

    public TextSearchInsight analyzeTextSearch(
            String keyword, String placeId, double lat, double lng, int radiusMeters) {
        return directGateway.analyzeTextSearch(keyword, placeId, lat, lng, radiusMeters);
    }

    public GooglePlaceSnapshot enrichWithReviewStats(GooglePlaceSnapshot snap) {
        return directGateway.enrichWithReviewStats(snap);
    }

    public record TextSearchInsight(int rank, List<GoogleRankedPlace> topPlaces) {}

    static String normalizePlaceId(String id) {
        if (id == null) return "";
        return id.startsWith("places/") ? id : "places/" + id;
    }
}
