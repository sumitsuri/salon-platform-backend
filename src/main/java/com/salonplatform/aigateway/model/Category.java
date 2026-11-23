package com.salonplatform.aigateway.model;

/** The kinds of risk the gateway recognises. */
public enum Category {
    CREDENTIAL,
    PAYMENT_CARD,
    GOVERNMENT_ID,
    BANK_ACCOUNT,
    PHONE,
    EMAIL,
    PROMPT_INJECTION,
    OFF_TOPIC,
    TOO_LONG,
    SYSTEM_PROMPT_LEAK
}
