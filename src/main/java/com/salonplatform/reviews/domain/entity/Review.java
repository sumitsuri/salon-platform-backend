package com.salonplatform.reviews.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reviews", indexes = {
        @Index(name = "idx_reviews_tenant_branch", columnList = "tenant_id, branch_id"),
        @Index(name = "idx_reviews_visit", columnList = "visit_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID invitationId;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID branchId;

    @Column(nullable = false)
    private UUID visitId;

    @Column(nullable = false)
    private int overallRating;

    private Integer serviceRating;

    private Integer ambienceRating;

    private Integer staffRating;

    private Integer cleanlinessRating;

    private Integer valueRating;

    @Column(length = 512)
    private String improvementTags;

    @Column(length = 2000)
    private String comment;

    @Builder.Default
    private boolean googleReviewRedirected = false;

    @CreationTimestamp
    private Instant submittedAt;
}
