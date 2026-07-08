package com.salonplatform.dto.attendance;

import com.salonplatform.domain.enums.AttendanceMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class ManualAttendanceRequest {
    @NotNull
    private UUID staffId;
    @NotNull
    private LocalDate workDate;
    private Instant entryTime;
    private Instant exitTime;
    private String reason;
}
