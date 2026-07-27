package com.salonplatform.sales.domain.entity;

import com.salonplatform.sales.domain.enums.LeadSource;
import com.salonplatform.sales.domain.enums.LeadStage;
import com.salonplatform.sales.domain.enums.LeadType;
import com.salonplatform.sales.domain.enums.BillingPeriod;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sales_leads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesLead {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String businessName;

    @Column(nullable = false)
    private String contactName;

    private String email;

    @Column(nullable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeadType leadType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LeadStage stage = LeadStage.NEW;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LeadSource source = LeadSource.FIELD;

    private UUID localityId;

    private String localityName;

    private String address;

    @Builder.Default
    private String city = "Bangalore";

    @Builder.Default
    private int expectedBranches = 1;

    @Column(columnDefinition = "TEXT")
    private String useCase;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** UUID reference to users.id — no FK for microservice extraction */
    private UUID assignedRepId;

    /** UUID reference to tenants.id after conversion */
    private UUID convertedTenantId;

    private BigDecimal projectedMrr;

    private String planTier;

    private BigDecimal quotedAmount;

    @Enumerated(EnumType.STRING)
    private BillingPeriod billingPeriod;

    /** Discount as percentage of quoted price (0–100). */
    private BigDecimal discountPercent;

    /** Discount amount in same currency as quoted price. */
    private BigDecimal discountAmount;

    /** Final price after discount (per billing period). */
    private BigDecimal finalPaidAmount;

    private String lostReason;

    private Instant trialIntentAt;

    private Instant convertedAt;

    private Instant nextFollowUpAt;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
