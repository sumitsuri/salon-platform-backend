package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.CampaignRunStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "campaign_runs", indexes = {
        @Index(name = "idx_campaign_runs_campaign", columnList = "campaign_id, started_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID campaignId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CampaignRunStatus status = CampaignRunStatus.SENDING;

    @Builder.Default
    private Integer recipientCount = 0;

    @Builder.Default
    private Integer sentCount = 0;

    @Builder.Default
    private Integer failedCount = 0;

    @CreationTimestamp
    private Instant startedAt;

    private Instant completedAt;
}
