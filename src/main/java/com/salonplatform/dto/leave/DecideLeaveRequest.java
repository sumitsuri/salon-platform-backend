package com.salonplatform.dto.leave;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DecideLeaveRequest {
    @Size(max = 500)
    private String note;
}
