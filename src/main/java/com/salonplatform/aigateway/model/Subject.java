package com.salonplatform.aigateway.model;

/** Who is asking. Plain strings, so the gateway does not depend on any application's user types. */
public record Subject(String tenantId, String userId, String role) {}
