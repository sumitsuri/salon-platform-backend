package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.AttendanceMethod;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "attendance_records", indexes = {
        @Index(name = "idx_attendance_staff_date", columnList = "staffId,workDate")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID branchId;

    @Column(nullable = false)
    private UUID staffId;

    @Column(nullable = false)
    private LocalDate workDate;

    private Instant entryTime;

    private Instant exitTime;

    @Enumerated(EnumType.STRING)
    private AttendanceMethod entryMethod;

    @Enumerated(EnumType.STRING)
    private AttendanceMethod exitMethod;

    private String manualReason;

    private UUID recordedByUserId;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
