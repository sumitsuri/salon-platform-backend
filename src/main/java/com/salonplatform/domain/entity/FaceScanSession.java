package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.FaceScanStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "face_scan_sessions", indexes = {
        @Index(name = "idx_face_scan_tenant_branch", columnList = "tenant_id, branch_id"),
        @Index(name = "idx_face_scan_customer", columnList = "customer_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaceScanSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID branchId;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private UUID performedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private FaceScanStatus status = FaceScanStatus.DRAFT;

    @Column(columnDefinition = "TEXT")
    private String reportJson;

    @Column(length = 512)
    private String staffNotes;

    @Column(length = 32)
    private String primaryConcernCode;

    private Integer skinHealthScore;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    private Instant analyzedAt;
}
