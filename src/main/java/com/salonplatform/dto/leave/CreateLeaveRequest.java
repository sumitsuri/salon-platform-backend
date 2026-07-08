package com.salonplatform.dto.leave;

import com.salonplatform.domain.enums.LeaveType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateLeaveRequest {
    @NotNull
    private UUID staffId;
    @NotNull
    private LocalDate startDate;
    @NotNull
    private LocalDate endDate;
    private LeaveType leaveType;
    private String reason;
}
