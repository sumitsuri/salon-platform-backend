package com.salonplatform.domain.enums;

public enum LeaveStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /** Withdrawn by the employee (or a manager) before it was decided. */
    CANCELLED
}
