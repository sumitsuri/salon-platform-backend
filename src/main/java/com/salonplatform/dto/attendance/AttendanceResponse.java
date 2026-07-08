package com.salonplatform.dto.attendance;

import com.salonplatform.domain.enums.AttendanceMethod;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class AttendanceResponse {
    private UUID id;
    private UUID staffId;
    private String staffName;
    private UUID branchId;
    private String branchName;
    private LocalDate workDate;
    private Instant entryTime;
    private Instant exitTime;
    private AttendanceMethod entryMethod;
    private AttendanceMethod exitMethod;
    private String manualReason;
    private Double hoursWorked;
    private String status;
}
