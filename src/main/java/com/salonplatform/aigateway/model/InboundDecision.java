package com.salonplatform.aigateway.model;

import java.util.List;

/**
 * The verdict on a conversation before it reaches a model.
 *
 * @param kind               what happens next
 * @param reasonCode         a stable machine-readable reason (for example OFF_TOPIC, CREDENTIAL, SPEND_MONTHLY)
 * @param userMessage        what to show the person when the request is not sent on; plain, kind, and never contains
 *                           the sensitive text
 * @param discardUserMessage the person's last message should be removed from any stored history (it held a secret)
 * @param messages           the conversation to send on (sensitive parts redacted); empty unless {@code kind} is ALLOW
 * @param findings           what was found, without the text
 * @param context            the request context to pass to the later stages
 */
public record InboundDecision(
        Kind kind,
        String reasonCode,
        String userMessage,
        boolean discardUserMessage,
        List<Message> messages,
        List<Finding> findings,
        GatewayContext context) {

    public enum Kind {
        /** Send the (possibly redacted) conversation to the model. */
        ALLOW,
        /** Do not call the model; show {@code userMessage}. */
        BLOCK,
        /** Do not call the model; {@code userMessage} is a complete answer (greeting, who-are-you, thanks). */
        ANSWERED,
        /** A quota, budget or cool-down stopped it; callers should answer with a "too many requests" style response. */
        LIMIT
    }

    public boolean allowed() {
        return kind == Kind.ALLOW;
    }
}
