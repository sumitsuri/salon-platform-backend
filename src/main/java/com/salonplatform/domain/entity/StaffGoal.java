package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.StaffGoalStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "staff_goals", indexes = {
        @Index(name = "idx_staff_goals_staff", columnList = "staffId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID staffId;

    @Column(nullable = false)
    private String title;

    private String description;

    private String metricUnit;

    private BigDecimal targetValue;

    private BigDecimal currentValue;

    private LocalDate periodStart;

    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StaffGoalStatus status = StaffGoalStatus.ACTIVE;

    private UUID createdByUserId;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
