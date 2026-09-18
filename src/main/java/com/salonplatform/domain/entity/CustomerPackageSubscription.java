package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.PackagePlanType;
import com.salonplatform.domain.enums.PackageRedemptionMode;
import com.salonplatform.domain.enums.PackageSubscriptionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "customer_package_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerPackageSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private UUID branchId;

    @Column(nullable = false)
    private UUID planId;

    @Column(nullable = false, length = 120)
    private String planName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private PackagePlanType planType = PackagePlanType.SERVICE_BUNDLE;

    @Column(precision = 12, scale = 2)
    private BigDecimal creditTotal;

    @Column(precision = 12, scale = 2)
    private BigDecimal creditRemaining;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PackageRedemptionMode redemptionMode;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid;

    private UUID purchaseInvoiceId;

    private UUID purchaseBookingId;

    @Column(nullable = false)
    private LocalDate purchasedOn;

    @Column(nullable = false)
    private LocalDate expiresOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PackageSubscriptionStatus status = PackageSubscriptionStatus.ACTIVE;

    private UUID soldByUserId;

    /** Stylist / staff attributed on the sale (targets & floor reporting). */
    private UUID soldByStaffId;

    @CreationTimestamp
    private Instant createdAt;
}
