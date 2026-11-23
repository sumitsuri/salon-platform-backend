package com.salonplatform.aigateway.model;

import java.time.Instant;
import java.util.UUID;

/** Carried through one request so later stages (tool results, the answer, usage) belong to the same audit trail. */
public record GatewayContext(
        UUID requestId, String channel, String profile, Subject subject, String provider, String model, Instant startedAt) {}
