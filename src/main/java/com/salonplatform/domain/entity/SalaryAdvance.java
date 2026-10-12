package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.AdvanceStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "salary_advances", indexes = {
        @Index(name = "idx_salary_advances_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_salary_advances_staff", columnList = "staff_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryAdvance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID branchId;

    @Column(nullable = false)
    private UUID staffId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal requestedAmount;

    @Column(nullable = false)
    private int requestedInstallments;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AdvanceStatus status = AdvanceStatus.PENDING;

    /** What the owner actually granted; may be less than requested. */
    @Column(precision = 12, scale = 2)
    private BigDecimal approvedAmount;

    private Integer approvedInstallments;

    /** First day of the month the first installment is taken from salary. */
    private LocalDate firstDeductionMonth;

    @Column(length = 500)
    private String decisionNote;

    private UUID decidedByUserId;

    private Instant decidedAt;

    private UUID createdByUserId;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
