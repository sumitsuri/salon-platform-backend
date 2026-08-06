package com.salonplatform.dto.branch;

import com.salonplatform.domain.enums.BranchBusinessType;
import com.salonplatform.domain.enums.BranchStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class BranchResponse {
    private UUID id;
    private String name;
    private String code;
    private String address;
    private String societyDefault;
    private String gstin;
    private String phone;
    private String openTime;
    private String closeTime;
    private Double latitude;
    private Double longitude;
    private Integer geofenceRadiusMeters;
    private Integer attendanceGraceMinutes;
    private BigDecimal monthlySalesTarget;
    private BranchStatus status;
    private BranchBusinessType businessType;
    private String googleReviewUrl;
    private Boolean googleReviewAutoPublish;
    private String googlePlaceId;
    private String googleMapsUrl;
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
    private Instant digitalPresenceUpdatedAt;
    private Instant createdAt;
}
