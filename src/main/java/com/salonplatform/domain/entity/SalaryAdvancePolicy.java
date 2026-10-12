package com.salonplatform.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row per brand: the advance budget and the rules that keep it shared fairly between employees. */
@Entity
@Table(name = "salary_advance_policies", uniqueConstraints = @UniqueConstraint(columnNames = "tenant_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryAdvancePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    /** Total money the brand is willing to have out as advances at any one time. */
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal poolAmount = BigDecimal.ZERO;

    /** No single employee may hold more than this share of the pool at once (0-100). */
    @Column(nullable = false)
    @Builder.Default
    private int maxPoolSharePercent = 25;

    /** An employee's outstanding advances may not exceed this many months of their salary. */
    @Column(nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal maxSalaryMultiple = BigDecimal.ONE;

    @Column(nullable = false)
    @Builder.Default
    private int maxInstallments = 6;

    /** Minimum days between one request and the next from the same employee. */
    @Column(nullable = false)
    @Builder.Default
    private int cooldownDays = 30;

    /** Lets the owner pause new requests without losing the settings. */
    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    private UUID updatedByUserId;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
