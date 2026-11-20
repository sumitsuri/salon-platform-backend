package com.salonplatform.copilot.tools;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One thing the assistant can look up. Tools run as the signed-in user, on the request thread, through the same
 * services as the screens, so tenant and branch limits apply exactly as they do everywhere else.
 */
public record CopilotTool(String name, String label, String description, String schemaJson, Handler handler) {

    @FunctionalInterface
    public interface Handler {
        /** Returns any JSON-serialisable result. Throw {@link IllegalArgumentException} for bad arguments. */
        Object run(JsonNode args);
    }
}
