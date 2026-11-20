package com.salonplatform.copilot.llm;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** One turn of a conversation in a model-neutral shape, so providers can be swapped without touching the loop. */
public record LlmMessage(Role role, List<Block> blocks) {

    public enum Role { USER, ASSISTANT }

    public sealed interface Block permits Text, ToolUse, ToolResult {}

    public record Text(String text) implements Block {}

    /** The model asking for a tool to be run. */
    public record ToolUse(String id, String name, JsonNode input) implements Block {}

    /** What the tool returned (always JSON text), or why it failed. */
    public record ToolResult(String toolUseId, String content, boolean error) implements Block {}

    public static LlmMessage user(String text) {
        return new LlmMessage(Role.USER, List.of(new Text(text)));
    }

    public static LlmMessage assistant(String text) {
        return new LlmMessage(Role.ASSISTANT, List.of(new Text(text)));
    }
}
