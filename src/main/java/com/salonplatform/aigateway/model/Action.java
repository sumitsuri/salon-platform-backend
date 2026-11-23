package com.salonplatform.aigateway.model;

/** What the gateway does with a message or a piece of data. */
public enum Action {
    /** Let it through unchanged. */
    ALLOW,
    /** Let it through with the sensitive parts replaced by placeholders. */
    REDACT,
    /** Do not send it to the model at all. */
    BLOCK
}
