package com.salonplatform.sales.dto;

import com.salonplatform.sales.domain.enums.LeadClaimStatus;
import com.salonplatform.sales.domain.enums.LeadStage;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class DiscoveredSalonPreview {
    private String googlePlaceId;
    private String businessName;
    private String address;
    private String phone;
    private Double rating;
    private Integer reviewCount;
    private String googleMapsUrl;
    private String websiteUrl;
    private String category;
    private String photoRef;
    private Integer photoCount;
    private Boolean openNow;
    private String hoursSummary;
    /** Distance from search area center in kilometres. */
    private Double distanceKm;
    /** @deprecated use {@link #leadId} and {@link #claimStatus} */
    private boolean alreadyLead;

    private UUID leadId;
    private LeadStage leadStage;
    private LeadClaimStatus claimStatus;
    private UUID claimedByRepId;
    private String claimedByRepName;
    private Instant claimExpiresAt;
    private boolean claimable;
}
