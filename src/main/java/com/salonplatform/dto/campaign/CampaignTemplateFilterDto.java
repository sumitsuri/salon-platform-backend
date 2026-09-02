package com.salonplatform.dto.campaign;

import com.salonplatform.domain.enums.BookingSource;
import com.salonplatform.domain.enums.CampaignMembershipFilter;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CampaignTemplateFilterDto {
    private Integer minVisitCount;
    private Integer maxVisitCount;
    private BigDecimal minLifetimeSpend;
    private BigDecimal maxLifetimeSpend;
    private String lastVisitFrom;
    private String lastVisitTo;
    private UUID branchId;
    private CampaignMembershipFilter membershipFilter;
    private Integer membershipExpiringWithinDays;
    private List<UUID> hasServiceIds;
    private List<UUID> excludeServiceIds;
    private List<UUID> hasServiceCategoryIds;
    private List<UUID> excludeServiceCategoryIds;
    private Integer maxOverallRating;
    private Integer minOverallRating;
    private Boolean hasSubmittedReview;
    private Boolean googleReviewNotSubmitted;
    private BookingSource bookingSource;
}
