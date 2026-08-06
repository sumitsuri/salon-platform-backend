package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.CompetitorType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "local_competitors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocalCompetitor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    /** Optional branch this competitor is tracked against. */
    private UUID branchId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CompetitorType competitorType = CompetitorType.LOCAL;

    private String address;
    private String notes;

    /** Manual benchmark metrics (Phase 3 — off-platform salons). */
    private BigDecimal revenuePerBranchDay;
    private BigDecimal avgTicket;
    private BigDecimal retailAttachPercent;
    private BigDecimal netMarginPercent;
    private BigDecimal repeatVisitRate;

    /** Public Google / GBP metrics for digital presence comparison. */
    private Double googleRating;
    private Integer googleReviewCount;
    private Integer googleLowRatingReviewCount;
    private Integer googleReviewsSampleSize;
    private Integer gbpPhotoCount;
    private Integer gbpVideoCount;
    private Boolean gbpHasPhone;
    private Integer estimatedSearchRank;

    private String googlePlaceId;
    private String googleMapsUrl;
    private Boolean googleAutoDiscovered;
    private Instant googleSyncedAt;

    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
