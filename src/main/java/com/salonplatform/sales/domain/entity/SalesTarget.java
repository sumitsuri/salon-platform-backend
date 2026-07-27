package com.salonplatform.sales.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "sales_targets", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"rep_id", "week_start_date"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID repId;

    @Column(nullable = false)
    private LocalDate weekStartDate;

    @Builder.Default
    private int targetLeads = 0;

    @Builder.Default
    private int targetVisits = 0;

    @Builder.Default
    private int targetPitches = 0;

    @Builder.Default
    private int targetTrials = 0;

    @Builder.Default
    private int targetConversions = 0;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
