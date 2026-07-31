package com.salonplatform.reviews.domain.entity;

import com.salonplatform.reviews.domain.enums.RecoveryStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_recoveries", indexes = {
        @Index(name = "idx_review_recoveries_branch_status", columnList = "branch_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewRecovery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID reviewId;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID branchId;

    @Column(nullable = false)
    private UUID visitId;

    @Column(nullable = false)
    private int overallRating;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RecoveryStatus status = RecoveryStatus.OPEN;

    @Column(length = 2000)
    private String notes;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    private Instant resolvedAt;
}
