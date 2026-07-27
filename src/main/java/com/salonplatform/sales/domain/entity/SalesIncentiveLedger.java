package com.salonplatform.sales.domain.entity;

import com.salonplatform.sales.domain.enums.IncentiveEventType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "sales_incentive_ledger")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesIncentiveLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID repId;

    @Column(nullable = false)
    private UUID leadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncentiveEventType eventType;

    @Column(nullable = false)
    private BigDecimal amountInr;

    @Column(nullable = false)
    private LocalDate weekStartDate;

    @CreationTimestamp
    private Instant computedAt;
}
