package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.BookingSource;
import com.salonplatform.domain.enums.BookingStatus;
import com.salonplatform.domain.enums.DiscountScope;
import com.salonplatform.domain.enums.DiscountType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID branchId;

    @Column(nullable = false)
    private UUID customerId;

    /** Staff user for desk bookings; null for customer online bookings. */
    private UUID createdByUserId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private BookingStatus status = BookingStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    private DiscountType billDiscountType;

    @Column(precision = 12, scale = 2)
    private BigDecimal billDiscountValue;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DiscountScope billDiscountScope = DiscountScope.BILL;

    private String billDiscountNote;

    private UUID couponId;

    private UUID offerId;

    private UUID membershipSubscriptionId;

    /** Membership plan to sell with this visit (fee on bill; discount preview before payment). */
    private UUID pendingMembershipPlanId;

    @Column(precision = 14, scale = 2)
    private BigDecimal membershipDiscountAmount;

    @Column(precision = 14, scale = 2)
    private BigDecimal promoDiscountAmount;

    private String notes;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private BookingSource source = BookingSource.WALK_IN;

    /** Scheduled start for online appointments. */
    private Instant scheduledStartAt;

    /** Scheduled end for online appointments (from service durations). */
    private Instant scheduledEndAt;

    /** Public token for customer manage link (cancel/reschedule). */
    private String manageToken;

    /** When service work began (walk-in: create time). Used for duration & floor blocks. */
    private Instant serviceStartedAt;

    /** Estimated visit end from catalog service durations (max staff load). */
    private Instant estimatedEndAt;

    /** Minutes from serviceStartedAt to completedAt / invoice. */
    private Integer actualDurationMinutes;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    private Instant completedAt;
}
