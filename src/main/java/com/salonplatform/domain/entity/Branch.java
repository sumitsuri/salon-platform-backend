package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.BranchStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "branches", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 10)
    private String code;

    private String address;
    private String societyDefault;
    private String gstin;
    private String phone;
    private String openTime;
    private String closeTime;

    /** Branch location for attendance geofence (WGS84). */
    private Double latitude;
    private Double longitude;

    @Builder.Default
    private Integer geofenceRadiusMeters = 150;

    @Builder.Default
    private Integer attendanceGraceMinutes = 15;

    /** Monthly revenue target for branch-level tracking */
    private BigDecimal monthlySalesTarget;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private BranchStatus status = BranchStatus.ACTIVE;

    /** Google Business review URL used after 4–5★ internal ratings. */
    private String googleReviewUrl;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
