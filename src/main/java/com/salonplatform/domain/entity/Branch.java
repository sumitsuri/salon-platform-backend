package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.BranchBusinessType;
import com.salonplatform.domain.enums.BranchStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "branches", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 10)
    private String code;

    private String address;
    private String societyDefault;
    private String gstin;
    private String phone;
    private String openTime;
    private String closeTime;

    /** Branch location for attendance geofence (WGS84). */
    private Double latitude;
    private Double longitude;

    @Builder.Default
    private Integer geofenceRadiusMeters = 150;

    @Builder.Default
    private Integer attendanceGraceMinutes = 15;

    /** Monthly revenue target for branch-level tracking */
    private BigDecimal monthlySalesTarget;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private BranchStatus status = BranchStatus.ACTIVE;

    /** Salon / spa positioning for Local Spotlight keyword and rival discovery. */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private BranchBusinessType businessType = BranchBusinessType.SALON;

    /**
     * When true (default), walk-in registration requires a valid customer phone.
     * When false, name + visit pass are sufficient; phone is optional.
     */
    @Builder.Default
    private Boolean phoneNumberRequired = true;

    /**
     * Branch GST override: null = inherit brand {@link com.salonplatform.domain.entity.Tenant#gstEnabled}.
     */
    private Boolean gstEnabled;

    /** Google Business review URL used after 4–5★ internal ratings. */
    private String googleReviewUrl;

    /** When true, 4★+ internal ratings auto-open the Google review page for the guest. */
    @Builder.Default
    private Boolean googleReviewAutoPublish = true;

    /** Google Maps / Business Profile identifiers and public metrics (manual or synced). */
    private String googlePlaceId;
    private String googleMapsUrl;
    private Double googleRating;
    private Integer googleReviewCount;
    private Integer googleLowRatingReviewCount;
    private Integer googleReviewsSampleSize;
    private Integer gbpPhotoCount;
    private Integer gbpVideoCount;
    private Boolean gbpHasPhone;
    private Boolean gbpHasWebsite;
    private Boolean gbpHasHours;
    private Boolean gbpHasBookButton;
    private Integer gbpServicesListedCount;
    /** Estimated local pack rank for primary locality keyword (1 = top). */
    private Integer estimatedSearchRank;
    /** Exact address from Google Business Profile when synced. */
    private String googleFormattedAddress;
    /** JSON array of keyword rank snapshots from last Google sync. */
    @Column(columnDefinition = "TEXT")
    private String googleSearchRankData;
    private Instant digitalPresenceUpdatedAt;

    /** Tracks branch-scoped catalog migration patches (e.g. Varthur printed menu). */
    private String catalogPatchVersion;

    @Builder.Default
    private Boolean onlineBookingEnabled = false;

    /** Minimum minutes before a slot can be booked (e.g. 120 = 2 hours). */
    @Builder.Default
    private Integer onlineBookingMinLeadMinutes = 60;

    /** Maximum days in advance customers can book. */
    @Builder.Default
    private Integer onlineBookingMaxAdvanceDays = 30;

    /** Slot grid step in minutes. */
    @Builder.Default
    private Integer onlineBookingSlotMinutes = 15;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
