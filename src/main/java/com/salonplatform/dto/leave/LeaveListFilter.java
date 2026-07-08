package com.salonplatform.dto.leave;

import com.salonplatform.domain.enums.LeaveStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class LeaveListFilter {
    private UUID branchId;
    private String staff;
    private String branch;
    private LeaveStatus status;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private int page;
    private int size;
}
