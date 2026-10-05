package com.salonplatform.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "local_spotlight_keyword_rank_daily", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"branch_id", "keyword", "snapshot_date"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocalSpotlightKeywordRankDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "pin_code", nullable = false, length = 6)
    private String pinCode;

    @Column(nullable = false)
    private String keyword;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    private Integer yourRank;

    @Column(name = "beyond_top_20", nullable = false)
    @Builder.Default
    private boolean beyondTop20 = false;

    @Column(name = "top_three_json", columnDefinition = "TEXT")
    private String topThreeJson;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
}
