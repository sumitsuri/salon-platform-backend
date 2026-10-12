package com.salonplatform.domain.enums;

public enum AdvanceStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    /** Approved and every installment has been deducted or waived. */
    COMPLETED
}
