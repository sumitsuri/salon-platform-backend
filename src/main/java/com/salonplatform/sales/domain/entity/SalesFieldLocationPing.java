package com.salonplatform.sales.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** A single GPS fix reported by a sales rep's device while "field mode" is active. */
@Entity
@Table(name = "sales_field_location_pings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesFieldLocationPing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID repId;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    private Double accuracyMeters;

    /** Client-reported fix time — createdAt below is server receive time. */
    @Column(nullable = false)
    private Instant capturedAt;

    private Instant createdAt;
}
