package com.salonplatform.dto.campaign;

import com.salonplatform.domain.enums.BookingSource;
import com.salonplatform.domain.enums.CampaignMembershipFilter;
import com.salonplatform.domain.enums.CampaignStatus;
import com.salonplatform.domain.enums.MessageChannel;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CampaignResponse {
    private UUID id;
    private String name;
    private MessageChannel channel;
    private CampaignStatus status;
    private String messageText;
    private String templateId;
    private String filterName;
    private String filterSociety;
    private String filterPhone;
    private Integer filterMinVisitCount;
    private Integer filterMaxVisitCount;
    private BigDecimal filterMinLifetimeSpend;
    private BigDecimal filterMaxLifetimeSpend;
    private LocalDate filterLastVisitFrom;
    private LocalDate filterLastVisitTo;
    private Boolean filterWhatsappOptInOnly;
    private Boolean filterSmsOptInOnly;
    private UUID filterBranchId;
    private CampaignMembershipFilter filterMembershipFilter;
    private Integer filterMembershipExpiringWithinDays;
    private List<UUID> filterHasServiceIds;
    private List<UUID> filterExcludeServiceIds;
    private List<UUID> filterHasServiceCategoryIds;
    private List<UUID> filterExcludeServiceCategoryIds;
    private Integer filterMaxOverallRating;
    private Integer filterMinOverallRating;
    private Boolean filterHasSubmittedReview;
    private Boolean filterGoogleReviewNotSubmitted;
    private BookingSource filterBookingSource;
    private Integer recipientCount;
    private Integer sentCount;
    private Integer failedCount;
    private Integer skippedCount;
    private Instant sentAt;
    private Instant createdAt;
    private Integer runCount;
    private Instant lastRunAt;
    private Boolean sendInProgress;
}
