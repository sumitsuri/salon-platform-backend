package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.CustomerIdentityStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(length = 15)
    private String phone;

    @Column(nullable = false, length = 32)
    private String visitPassId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CustomerIdentityStatus identityStatus = CustomerIdentityStatus.PHONE_VERIFIED;

    /** Opaque token for public registration-card URL (no auth). */
    private String passPublicToken;

    private String society;
    private String flatUnit;
    private String notes;

    /** Reserved for future SES integration. */
    private String email;

    @Builder.Default
    private Boolean whatsappOptIn = true;

    @Builder.Default
    private Boolean smsOptIn = true;

    @Builder.Default
    private Integer visitCount = 0;

    @Builder.Default
    @Column(precision = 14, scale = 2)
    private BigDecimal lifetimeSpend = BigDecimal.ZERO;

    private Instant lastVisitAt;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
