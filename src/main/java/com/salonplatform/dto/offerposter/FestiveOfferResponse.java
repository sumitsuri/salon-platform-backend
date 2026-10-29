package com.salonplatform.dto.offerposter;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class FestiveOfferResponse {
    private UUID id;
    private UUID branchId;
    private String branchName;
    private String occasion;
    private String occasionLabel;
    private String headline;
    private String tagline;
    private String brandName;
    private String address;
    private String phone;
    private LocalDate validUntil;
    private String terms;
    private List<OfferBlockDto> blocks;
    private List<String> includedServices;
    private String style;
    private String format;
    private String extraDirection;
    private String caption;
    /** Plain-language summary of each offer, exactly as it will be stated on the poster. */
    private List<String> summaryLines;
    private PosterResponse latestPoster;
    private int posterCount;
    private Instant createdAt;
    private Instant updatedAt;
}
