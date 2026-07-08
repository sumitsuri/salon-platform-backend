package com.salonplatform.dto.attendance;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class AttendanceListFilter {
    private UUID branchId;
    private String staff;
    private String branch;
    private String status;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private int page;
    private int size;
}
