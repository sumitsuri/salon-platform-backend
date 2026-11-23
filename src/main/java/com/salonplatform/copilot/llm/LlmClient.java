package com.salonplatform.copilot.llm;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.function.Consumer;

/** Runs one model turn, streaming text as it is produced. */
public interface LlmClient {

    /** A tool the model may call; {@code inputSchema} is a JSON Schema object. */
    record ToolSpec(String name, String description, JsonNode inputSchema) {}

    /** The finished turn: assistant blocks (text, then any tool calls) and token usage. */
    record Turn(List<LlmMessage.Block> blocks, String stopReason, int inputTokens, int outputTokens) {

        public List<LlmMessage.ToolUse> toolUses() {
            return blocks.stream()
                    .filter(LlmMessage.ToolUse.class::isInstance)
                    .map(LlmMessage.ToolUse.class::cast)
                    .toList();
        }
    }

    /** The model could not be reached or refused; the message is safe to show to the user. */
    class LlmException extends RuntimeException {
        public LlmException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    Turn stream(String system, List<LlmMessage> messages, List<ToolSpec> tools, Consumer<String> onText);

    /** A short, non-streaming answer from a (usually small) model. */
    record Completion(String text, long inputTokens, long outputTokens) {}

    /**
     * One quick call to {@code modelId}, for jobs like classifying a question. Returns empty when this provider cannot
     * do it or the call failed; callers must then use a fallback.
     */
    default java.util.Optional<Completion> complete(String modelId, String system, String user, int maxTokens) {
        return java.util.Optional.empty();
    }
}
