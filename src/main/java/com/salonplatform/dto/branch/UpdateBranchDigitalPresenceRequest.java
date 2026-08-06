package com.salonplatform.dto.branch;

import lombok.Data;

@Data
public class UpdateBranchDigitalPresenceRequest {
    private String googlePlaceId;
    private String googleMapsUrl;
    private String googleReviewUrl;
    private Double googleRating;
    private Integer googleReviewCount;
    private Integer gbpPhotoCount;
    private Integer gbpVideoCount;
    private Boolean gbpHasPhone;
    private Boolean gbpHasWebsite;
    private Boolean gbpHasHours;
    private Boolean gbpHasBookButton;
    private Integer gbpServicesListedCount;
    private Integer estimatedSearchRank;
    private Boolean googleReviewAutoPublish;
}
