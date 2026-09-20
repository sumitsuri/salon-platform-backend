package com.salonplatform.sales.domain.entity;

import com.salonplatform.sales.domain.enums.MapSyncStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sales_map_sync_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesMapSyncJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MapSyncStatus status;

    /** When set, only this area is synced; otherwise all mappable localities. */
    private UUID localityId;

    private String localityName;

    @Column(nullable = false)
    private int radiusKm;

    private int totalAreas;
    private int completedAreas;
    private String currentAreaName;
    private int leadsInserted;
    private int leadsSkippedDuplicate;
    private int areaErrors;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private UUID triggeredByUserId;

    private Instant startedAt;
    private Instant finishedAt;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
