package com.salonplatform.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "local_spotlight_serp_cache", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"cache_key", "snapshot_date"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocalSpotlightSerpCache {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cache_key", nullable = false, length = 160)
    private String cacheKey;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "serp_json", nullable = false, columnDefinition = "TEXT")
    private String serpJson;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
}
