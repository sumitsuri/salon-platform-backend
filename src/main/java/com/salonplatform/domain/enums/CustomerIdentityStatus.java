package com.salonplatform.domain.enums;

public enum CustomerIdentityStatus {
    /** Registered with a valid phone at creation or on a phone-required branch. */
    PHONE_VERIFIED,
    /** No phone on file; identified by visit pass only. */
    PASS_ONLY,
    /** Started as pass-only; phone added later. */
    UPGRADED
}
