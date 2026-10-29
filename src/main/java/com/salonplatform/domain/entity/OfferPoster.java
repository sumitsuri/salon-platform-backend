package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.OfferPosterStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** One generated poster image for an offer. Several can exist per offer (regenerations). */
@Entity
@Table(name = "offer_posters", indexes = {
        @Index(name = "idx_offer_posters_offer", columnList = "offerId, createdAt"),
        @Index(name = "idx_offer_posters_tenant_day", columnList = "tenantId, createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfferPoster {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID offerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OfferPosterStatus status = OfferPosterStatus.PENDING;

    @Column(length = 30)
    private String style;

    @Column(length = 20)
    private String format;

    /** Exact prompt sent to the image model, kept so a result can be understood and reproduced. */
    @Column(length = 8000)
    private String prompt;

    @Column(length = 30)
    private String provider;

    @Column(length = 60)
    private String model;

    private String imageKey;

    @Column(length = 40)
    private String contentType;

    /** Owner-facing reason when generation failed. */
    @Column(length = 400)
    private String error;

    private UUID createdBy;

    @CreationTimestamp
    private Instant createdAt;

    private Instant completedAt;
}
