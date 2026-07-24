package com.salonplatform.dto.attendance;

import com.salonplatform.domain.enums.GeoStatus;
import com.salonplatform.domain.enums.IncidentType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class AttendanceIncidentResponse {
    private UUID id;
    private UUID staffId;
    private String staffName;
    private UUID branchId;
    private UUID attendanceRecordId;
    private java.time.LocalDate workDate;
    private IncidentType type;
    private String note;
    private BigDecimal penaltyAmount;
    private UUID createdByUserId;
    private Instant createdAt;
}
