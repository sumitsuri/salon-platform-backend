package com.salonplatform.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A festive offer an owner designs for one branch and turns into a shareable poster. This is marketing content only:
 * it does not create or change any discount that billing applies.
 */
@Entity
@Table(name = "festive_offers", indexes = {
        @Index(name = "idx_festive_offers_tenant", columnList = "tenantId, updatedAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FestiveOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID branchId;

    /** {@code PosterOccasion} name. */
    @Column(nullable = false, length = 40)
    private String occasion;

    @Column(nullable = false, length = 120)
    private String headline;

    @Column(length = 160)
    private String tagline;

    /** Name shown on the poster, e.g. "Mystic Salon". */
    @Column(nullable = false, length = 80)
    private String brandName;

    @Column(length = 200)
    private String address;

    @Column(length = 30)
    private String phone;

    private LocalDate validUntil;

    @Column(length = 400)
    private String terms;

    /** JSON array of offer blocks (see OfferBlockDto). */
    @Column(nullable = false, length = 12000)
    private String blocksJson;

    /** JSON array of service names for the "Salon services include" panel (optional). */
    @Column(length = 2000)
    private String includedServicesJson;

    /** {@code PosterStyle} name. */
    @Column(nullable = false, length = 30)
    private String style;

    /** {@code PosterFormat} name. */
    @Column(nullable = false, length = 20)
    private String format;

    /** Extra art direction from the owner, e.g. "include a bride with mehendi". */
    @Column(length = 400)
    private String extraDirection;

    /** The WhatsApp message that goes with the poster. */
    @Column(length = 2000)
    private String caption;

    private UUID createdBy;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
