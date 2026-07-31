package com.salonplatform.reviews.domain.entity;

import com.salonplatform.reviews.domain.enums.ReviewInvitationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_invitations", indexes = {
        @Index(name = "idx_review_invitations_visit", columnList = "visit_id"),
        @Index(name = "idx_review_invitations_tenant_branch", columnList = "tenant_id, branch_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID branchId;

    @Column(nullable = false)
    private String branchName;

    @Column(nullable = false)
    private UUID visitId;

    private UUID invoiceId;

    private UUID customerId;

    private String customerFirstName;

    private String googleReviewUrl;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ReviewInvitationStatus status = ReviewInvitationStatus.PENDING;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant submittedAt;

    @CreationTimestamp
    private Instant createdAt;
}
