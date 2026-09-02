package com.salonplatform.dto.campaign;

import com.salonplatform.domain.enums.BookingSource;
import com.salonplatform.domain.enums.CampaignMembershipFilter;
import com.salonplatform.domain.enums.MessageChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class CreateCampaignRequest {
    @NotBlank
    @Size(max = 120)
    private String name;

    @NotNull
    private MessageChannel channel;

    @NotBlank
    @Size(max = 500)
    private String messageText;

    /** Optional reference to a growth template from the library. */
    private String templateId;

    private String filterName;
    private List<String> filterNames;
    private String filterSociety;
    private String filterPhone;
    private List<String> filterPhones;
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
}
