package com.salonplatform.domain.enums;

/**
 * Brand-level switch for customer WhatsApp/SMS. SIMULATE records the message as sent without
 * calling MSG91 — new brands start here until platform flips them to LIVE.
 */
public enum OutboundMessagingMode {
    LIVE,
    SIMULATE
}
