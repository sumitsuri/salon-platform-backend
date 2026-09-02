package com.salonplatform.campaign;

import com.salonplatform.domain.enums.BookingSource;
import com.salonplatform.domain.enums.CampaignMembershipFilter;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resolved filter preset applied when a growth template is selected.
 * Relative day offsets are resolved to ISO dates at serve time.
 */
@Data
@Builder
public class CampaignTemplateFilterPreset {
    private Integer minVisitCount;
    private Integer maxVisitCount;
    private BigDecimal minLifetimeSpend;
    private BigDecimal maxLifetimeSpend;
    /** Sets filterLastVisitFrom = today minus N days. */
    private Integer lastVisitFromDaysAgo;
    /** Sets filterLastVisitTo = today minus N days (last visit before N days ago). */
    private Integer lastVisitToDaysAgo;
    private CampaignMembershipFilter membershipFilter;
    private Integer membershipExpiringWithinDays;
    private List<String> serviceKeywords;
    private List<String> excludeServiceKeywords;
    private List<String> serviceCategoryKeywords;
    private List<String> excludeServiceCategoryKeywords;
    private Integer maxOverallRating;
    private Integer minOverallRating;
    private Boolean hasSubmittedReview;
    private Boolean googleReviewNotSubmitted;
    private BookingSource bookingSource;
}
