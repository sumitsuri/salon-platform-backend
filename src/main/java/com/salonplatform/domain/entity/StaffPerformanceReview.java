package com.salonplatform.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "staff_performance_reviews", indexes = {
        @Index(name = "idx_staff_reviews_staff", columnList = "staffId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffPerformanceReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID staffId;

    @Column(nullable = false)
    private String periodLabel;

    private LocalDate reviewDate;

    /** 1–5 overall rating */
    private BigDecimal overallRating;

    private String strengths;

    private String improvements;

    private String managerNotes;

    /** Attendance compliance % for the review window */
    private BigDecimal attendanceScore;

    /** Sales target achievement % for the review window */
    private BigDecimal salesAchievementPercent;

    @Builder.Default
    private boolean visibleToStaff = true;

    private UUID reviewedByUserId;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
