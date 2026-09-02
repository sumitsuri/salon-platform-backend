package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.CampaignStatus;
import com.salonplatform.domain.enums.MessageChannel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "marketing_campaigns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketingCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CampaignStatus status = CampaignStatus.ACTIVE;

    /** Promo text — WhatsApp template body var or SMS flow variable. */
    @Column(nullable = false, length = 500)
    private String messageText;

    private String filterName;
    private String filterSociety;
    private String filterPhone;
    private Integer filterMinVisitCount;
    private Integer filterMaxVisitCount;
    private BigDecimal filterMinLifetimeSpend;
    private BigDecimal filterMaxLifetimeSpend;
    private LocalDate filterLastVisitFrom;
    private LocalDate filterLastVisitTo;

    @Builder.Default
    private Boolean filterWhatsappOptInOnly = true;

    @Builder.Default
    private Boolean filterSmsOptInOnly = true;

    private UUID filterBranchId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private com.salonplatform.domain.enums.CampaignMembershipFilter filterMembershipFilter;

    private Integer filterMembershipExpiringWithinDays;

    /** JSON array or single UUID string. */
    private String filterHasServiceIds;
    private String filterExcludeServiceIds;
    private String filterHasServiceCategoryIds;
    private String filterExcludeServiceCategoryIds;

    private Integer filterMaxOverallRating;
    private Integer filterMinOverallRating;
    private Boolean filterHasSubmittedReview;
    private Boolean filterGoogleReviewNotSubmitted;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private com.salonplatform.domain.enums.BookingSource filterBookingSource;

    /** Growth template reference, if campaign was created from library. */
    @Column(length = 80)
    private String templateId;

    @Builder.Default
    private Integer recipientCount = 0;

    @Builder.Default
    private Integer sentCount = 0;

    @Builder.Default
    private Integer failedCount = 0;

    private UUID createdByUserId;
    private Instant sentAt;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
