package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.SalonTier;
import com.salonplatform.domain.enums.TenantStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    private String logoUrl;

    @Builder.Default
    private String primaryColor = "#6366f1";

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TenantStatus status = TenantStatus.ACTIVE;

    @Builder.Default
    private String defaultLocale = "en-IN";

    /** Opt-in to anonymized cross-brand benchmark cohort (Market Pulse). */
    @Builder.Default
    private Boolean benchmarkOptIn = true;

    @Builder.Default
    private String marketCity = "Bangalore";

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SalonTier salonTier = SalonTier.MID_MARKET;

    /**
     * When true, service GST rates apply on bills. Default false for all brands/environments.
     */
    @Builder.Default
    private Boolean gstEnabled = false;

    /** Brand-wide master switch for customer online booking. Branches can opt out individually. */
    @Builder.Default
    private Boolean onlineBookingEnabled = false;

    /** Tracks one-off catalog migration patches applied to this tenant. */
    private String catalogPatchVersion;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
