package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.PackageRedemptionMode;
import com.salonplatform.domain.enums.PromoStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_package_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServicePackagePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 120)
    private String name;

    private String description;

    /** Sum of branch list prices at creation (informational). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal listPriceTotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal packagePrice;

    @Column(nullable = false)
    @Builder.Default
    private Integer validityDays = 90;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private PackageRedemptionMode redemptionMode = PackageRedemptionMode.MULTI_VISIT;

    @Column(length = 2000)
    private String branchIds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PromoStatus status = PromoStatus.ACTIVE;

    /** System template rank 1–20; null for custom packages. */
    private Integer predefinedRank;

    private UUID createdByUserId;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
