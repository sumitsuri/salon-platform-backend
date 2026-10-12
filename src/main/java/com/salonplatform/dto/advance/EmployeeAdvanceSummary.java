package com.salonplatform.dto.advance;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** How often an employee has asked for advances, how much, and how much of the shared budget they hold. */
@Data
@Builder
public class EmployeeAdvanceSummary {
    private UUID staffId;
    private String staffName;
    private UUID branchId;
    private String branchName;
    private String role;
    private boolean active;
    private BigDecimal monthlySalary;
    private int requestCount;
    private int approvedCount;
    private int rejectedCount;
    private BigDecimal totalRequested;
    private BigDecimal totalApproved;
    private BigDecimal outstanding;
    /** Outstanding as a share of the pool, 0-100. */
    private int poolSharePct;
    private Instant lastRequestedAt;
    private int requestsLast180Days;
    /** Three or more requests in 180 days — a nudge to look at the pattern, not an automatic block. */
    private boolean repeatRequester;
    /** What this employee could be granted right now under the rules. */
    private BigDecimal eligibleMax;
}
