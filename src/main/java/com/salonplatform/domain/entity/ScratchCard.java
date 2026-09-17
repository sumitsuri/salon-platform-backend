package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.ScratchCardStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scratch_cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScratchCard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID campaignId;

    @Column(nullable = false)
    private UUID branchId;

    @Column(nullable = false, unique = true, length = 64)
    private String publicToken;

    private UUID bookingId;

    private UUID customerId;

    private UUID prizeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ScratchCardStatus status = ScratchCardStatus.ISSUED;

    @Column(length = 20)
    private String customerPhone;

    @Column(length = 120)
    private String customerName;

    private UUID couponId;

    @Column(length = 12)
    private String redemptionCode;

    private UUID issuedByUserId;

    private Instant scratchedAt;

    private Instant contactCapturedAt;

    private Instant redeemedAt;

    private UUID redeemedBookingId;

    @Column(nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    private Instant createdAt;
}
