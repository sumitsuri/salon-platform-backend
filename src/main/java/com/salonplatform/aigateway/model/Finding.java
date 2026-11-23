package com.salonplatform.aigateway.model;

/**
 * Something sensitive found in a text. It records where and what kind, never the matched text itself, so findings
 * are safe to log and audit.
 */
public record Finding(Category category, String ruleId, int start, int end) {}
