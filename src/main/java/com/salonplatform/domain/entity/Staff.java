package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.StaffRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "staff")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID branchId;

    @Column(nullable = false)
    private String name;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StaffRole role = StaffRole.STYLIST;

    private String skills;

    /** Simulated biometric thumb ID registered on device */
    private String biometricId;

    /** Monthly base salary — CEO-only field */
    private BigDecimal salary;

    private LocalDate joiningDate;

    @Builder.Default
    @Column(columnDefinition = "boolean default false")
    private Boolean idProofCollected = false;

    /** Masked reference e.g. "Aadhaar XXXX1234" — CEO-only */
    private String idProofReference;

    /** Monthly sales target in INR */
    private BigDecimal monthlySalesTarget;

    /** Incentive % of target paid when target is achieved (e.g. 5 = 5%) */
    private BigDecimal incentivePercent;

    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
