package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.InstallmentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One month's recovery of an approved advance, taken from that month's salary. */
@Entity
@Table(name = "salary_advance_installments", indexes = {
        @Index(name = "idx_advance_installments_advance", columnList = "advance_id"),
        @Index(name = "idx_advance_installments_tenant_month", columnList = "tenant_id, due_month, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryAdvanceInstallment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID advanceId;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID staffId;

    /** First day of the salary month this installment is due in. */
    @Column(nullable = false)
    private LocalDate dueMonth;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InstallmentStatus status = InstallmentStatus.SCHEDULED;

    private Instant settledAt;

    private UUID settledByUserId;

    @Column(length = 300)
    private String note;

    @CreationTimestamp
    private Instant createdAt;
}
