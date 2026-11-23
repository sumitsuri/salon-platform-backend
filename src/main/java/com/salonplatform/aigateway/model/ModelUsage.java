package com.salonplatform.aigateway.model;

/** What a model call used, reported back to the gateway so cost can be tracked per subject. */
public record ModelUsage(String model, long inputTokens, long outputTokens, long latencyMillis, boolean success) {}
