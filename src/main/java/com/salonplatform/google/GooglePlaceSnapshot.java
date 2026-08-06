package com.salonplatform.google;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GooglePlaceSnapshot {
    private String placeId;
    private String name;
    private String formattedAddress;
    private Double latitude;
    private Double longitude;
    private Double rating;
    private Integer reviewCount;
    private Integer photoCount;
    private String googleMapsUri;
    private String websiteUri;
    private String phone;
    private boolean hasOpeningHours;
    private String primaryType;
    /** Count of public reviews with rating below 4 stars in Google's returned review sample. */
    private Integer lowRatingReviewCount;
    private Integer reviewsSampleSize;

    public String reviewUrl() {
        String raw = placeResourceId();
        if (raw == null) return null;
        return "https://search.google.com/local/writereview?placeid=" + raw;
    }

    public String mapsUriOrFallback() {
        if (googleMapsUri != null && !googleMapsUri.isBlank()) {
            return googleMapsUri;
        }
        if (latitude != null && longitude != null) {
            return "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;
        }
        return null;
    }

    /** Strip `places/` prefix for legacy placeid parameters. */
    public String placeResourceId() {
        if (placeId == null || placeId.isBlank()) return null;
        return placeId.startsWith("places/") ? placeId.substring("places/".length()) : placeId;
    }
}
