package com.salonplatform.aigateway.ledger;

import com.salonplatform.aigateway.model.Category;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * One line of the audit trail. It never holds message text: only who, what was decided, why, which kinds of sensitive
 * data were seen, and what it cost.
 */
public record AuditEvent(
        UUID id,
        Instant at,
        String kind,
        String channel,
        String tenantId,
        String userId,
        String decision,
        String reason,
        Set<Category> categories,
        String ruleIds,
        String provider,
        String model,
        long tokensIn,
        long tokensOut,
        BigDecimal costInr,
        long latencyMillis,
        int inputChars) {

    /** A question was let through to the model. */
    public static final String REQUEST = "REQUEST";
    /** What a model call used. */
    public static final String USAGE = "USAGE";
    /** A question was refused for what it asked or contained (counts towards a cool-down). */
    public static final String BLOCK = "BLOCK";
    /** A quota or budget refused it. */
    public static final String LIMIT = "LIMIT";
    /** Answered from a fixed text without a model. */
    public static final String ANSWERED = "ANSWERED";
    /** The scope classifier ran. */
    public static final String CLASSIFY = "CLASSIFY";
    /** Sensitive data was redacted from a question, a tool result or an answer. */
    public static final String REDACT = "REDACT";
}
