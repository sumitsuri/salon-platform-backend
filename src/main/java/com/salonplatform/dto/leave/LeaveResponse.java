package com.salonplatform.dto.leave;

import com.salonplatform.domain.enums.LeaveStatus;
import com.salonplatform.domain.enums.LeaveType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class LeaveResponse {
    private UUID id;
    private UUID staffId;
    private String staffName;
    private UUID branchId;
    private String branchName;
    private LocalDate startDate;
    private LocalDate endDate;
    private LeaveType leaveType;
    private LeaveStatus status;
    private String reason;
    private Instant createdAt;
    /** Calendar days requested (half days count as 0.5). */
    private double days;
    private String decisionNote;
    private Instant decidedAt;
    /** Approved leave days this calendar year — only filled for pending requests, to inform the decision. */
    private Double staffApprovedDaysThisYear;
}
