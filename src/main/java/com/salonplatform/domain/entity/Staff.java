package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.StaffRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

    /** When the employee was soft-deactivated; null while active. Reports use it to keep history before this point. */
    private Instant deactivatedAt;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    /** Last calendar day this employee still counts on the roster; null while active (no end). */
    public LocalDate lastRosterDate(ZoneId zone) {
        return active || deactivatedAt == null ? null : deactivatedAt.atZone(zone).toLocalDate();
    }

    /** True if the employee was on the roster on {@code day} or later (still active, or deactivated on/after it). */
    public boolean onRosterOnOrAfter(LocalDate day, ZoneId zone) {
        if (active) return true;
        LocalDate last = lastRosterDate(zone);
        return last != null && !last.isBefore(day);
    }
}
