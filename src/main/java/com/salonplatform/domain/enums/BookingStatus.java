package com.salonplatform.domain.enums;

public enum BookingStatus {
    DRAFT,
    /** Online appointment booked; service not started yet. */
    CONFIRMED,
    IN_PROGRESS,
    READY_FOR_BILLING,
    COMPLETED,
    CANCELLED
}
