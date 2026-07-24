package com.salonplatform.dto.attendance;

import com.salonplatform.domain.enums.GeoStatus;
import com.salonplatform.domain.enums.IncidentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class CreateAttendanceIncidentRequest {
    @NotNull
    private UUID staffId;
    private UUID attendanceRecordId;
    private LocalDate workDate;
    @NotNull
    private IncidentType type;
    @NotBlank
    private String note;
    private BigDecimal penaltyAmount;
}
