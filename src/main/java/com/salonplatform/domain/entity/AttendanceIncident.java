package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.IncidentType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "attendance_incidents", indexes = {
        @Index(name = "idx_attendance_incident_staff", columnList = "staffId,workDate")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID staffId;

    private UUID branchId;

    private UUID attendanceRecordId;

    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentType type;

    @Column(nullable = false, length = 2000)
    private String note;

    private BigDecimal penaltyAmount;

    @Column(nullable = false)
    private UUID createdByUserId;

    @CreationTimestamp
    private java.time.Instant createdAt;
}
