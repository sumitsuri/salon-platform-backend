package com.salonplatform.dto.analytics;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class UpsertLocalCompetitorRequest {
    private String name;
    private String competitorType;
    private UUID branchId;
    private String address;
    private String notes;
    private BigDecimal revenuePerBranchDay;
    private BigDecimal avgTicket;
    private BigDecimal retailAttachPercent;
    private BigDecimal netMarginPercent;
    private BigDecimal repeatVisitRate;
    private Double googleRating;
    private Integer googleReviewCount;
    private Integer gbpPhotoCount;
    private Integer gbpVideoCount;
    private Boolean gbpHasPhone;
    private Integer estimatedSearchRank;
}
