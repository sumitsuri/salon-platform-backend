package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.MembershipCadence;
import com.salonplatform.domain.enums.PromoStatus;
import com.salonplatform.domain.enums.ServiceScopeType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "membership_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 120)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipCadence cadence;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal feeAmount;

    /** Flat percent benefit, e.g. 10.00 = 10% off. */
    @Column(nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal benefitPercent = new BigDecimal("10.00");

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ServiceScopeType serviceScope = ServiceScopeType.ALL;

    @Column(length = 2000)
    private String scopeIds;

    @Column(length = 2000)
    private String branchIds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PromoStatus status = PromoStatus.ACTIVE;

    private UUID createdByUserId;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
