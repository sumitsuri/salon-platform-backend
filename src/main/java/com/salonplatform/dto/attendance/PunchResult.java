package com.salonplatform.dto.attendance;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PunchResult {
    private String action;
    private AttendanceResponse record;
    private String message;
}
