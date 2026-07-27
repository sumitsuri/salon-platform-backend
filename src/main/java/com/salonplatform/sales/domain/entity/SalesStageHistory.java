package com.salonplatform.sales.domain.entity;

import com.salonplatform.sales.domain.enums.LeadStage;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sales_stage_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesStageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID leadId;

    @Enumerated(EnumType.STRING)
    private LeadStage fromStage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeadStage toStage;

    @Column(nullable = false)
    private UUID changedByUserId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    private Instant createdAt;
}
