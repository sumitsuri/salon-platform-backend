package com.salonplatform.sales.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class ClaimDiscoverSalonRequest {

    @NotBlank
    private String googlePlaceId;

    private UUID localityId;

    private Integer radiusKm;

    /** Populated from live search when lead is not yet in CRM. */
    private String businessName;
    private String address;
    private String phone;
    private String googleMapsUrl;
}
