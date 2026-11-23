package com.salonplatform.copilot.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.salonplatform.config.CopilotProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Anthropic API adapter (the official Java SDK, streaming Messages API). Use it with an API key from the Claude
 * Console, set as {@code ANTHROPIC_API_KEY}. The model is a setting, so models can be compared without code changes.
 *
 * <p>No assistant prefill, temperature or forced tool choice is sent: current models reject them. Thinking is left at
 * its default and depth is controlled with {@code app.copilot.effort}.
 */
@Slf4j
@Component
@ConditionalOnExpression("${app.copilot.enabled:false} and '${app.copilot.provider:bedrock}' == 'anthropic'")
public class AnthropicLlmClient implements LlmClient {

    private final AnthropicClient client;
    private final CopilotProperties props;
    private final ObjectMapper mapper;

    public AnthropicLlmClient(CopilotProperties props, ObjectMapper mapper) {
        if (props.getAnthropicApiKey() == null || props.getAnthropicApiKey().isBlank()) {
            throw new IllegalStateException(
                    "app.copilot.provider=anthropic needs ANTHROPIC_API_KEY (an API key from the Claude Console).");
        }
        this.props = props;
        this.mapper = mapper;
        this.client = AnthropicOkHttpClient.builder().apiKey(props.getAnthropicApiKey()).build();
    }

    @Override
    public Turn stream(String system, List<LlmMessage> messages, List<ToolSpec> tools, Consumer<String> onText) {
        MessageCreateParams.Builder request = MessageCreateParams.builder()
                .model(props.resolvedModelId())
                .maxTokens((long) props.getMaxTokens())
                .system(system);
        for (LlmMessage m : messages) {
            request.addMessage(toMessage(m));
        }
        for (ToolSpec t : tools) {
            request.addTool(toTool(t));
        }
        OutputConfig.Effort effort = effort();
        if (effort != null) {
            request.outputConfig(OutputConfig.builder().effort(effort).build());
        }

        MessageAccumulator accumulator = MessageAccumulator.create();
        Message message;
        try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(request.build())) {
            stream.stream().forEach(event -> {
                accumulator.accumulate(event);
                event.contentBlockDelta()
                        .flatMap(d -> d.delta().text())
                        .ifPresent(t -> onText.accept(t.text()));
            });
            message = accumulator.message();
        } catch (AnthropicServiceException e) {
            throw toLlmException(e);
        } catch (RuntimeException e) {
            throw toLlmException(e);
        }

        String stop = message.stopReason().map(r -> r.asString()).orElse("unknown");
        if ("refusal".equals(stop)) {
            throw new LlmException("The assistant could not answer that one. Try asking it a different way.", null);
        }

        List<LlmMessage.Block> blocks = new ArrayList<>();
        for (ContentBlock block : message.content()) {
            block.text().ifPresent(t -> {
                if (!t.text().isEmpty()) blocks.add(new LlmMessage.Text(t.text()));
            });
            block.toolUse().ifPresent(u -> blocks.add(new LlmMessage.ToolUse(u.id(), u.name(), toJson(u._input()))));
        }
        return new Turn(blocks, stop, (int) message.usage().inputTokens(), (int) message.usage().outputTokens());
    }

    private OutputConfig.Effort effort() {
        String e = props.getEffort() == null ? "" : props.getEffort().trim().toLowerCase();
        return switch (e) {
            case "low" -> OutputConfig.Effort.LOW;
            case "medium" -> OutputConfig.Effort.MEDIUM;
            case "high" -> OutputConfig.Effort.HIGH;
            default -> null; // blank: use the model's own default
        };
    }

    private LlmException toLlmException(RuntimeException e) {
        // Billing or key problems are ours to fix, not the owner's: log the detail, show something neutral.
        if (e instanceof AnthropicServiceException s) {
            log.error("Anthropic API call failed: status={} message={}", s.statusCode(), s.getMessage());
            if (s.statusCode() == 429 || s.statusCode() == 529) {
                return new LlmException("The assistant is busy right now. Please try again in a moment.", e);
            }
        } else {
            log.error("Anthropic API call failed", e);
        }
        return new LlmException("The assistant is temporarily unavailable. Please try again shortly.", e);
    }

    // ---- model-neutral messages to SDK types ----

    MessageParam toMessage(LlmMessage m) {
        List<ContentBlockParam> blocks = m.blocks().stream().map(this::toBlock).toList();
        return m.role() == LlmMessage.Role.USER
                ? MessageParam.ofUserOfContentBlockParams(blocks)
                : MessageParam.ofAssistantOfContentBlockParams(blocks);
    }

    private ContentBlockParam toBlock(LlmMessage.Block block) {
        return switch (block) {
            case LlmMessage.Text t -> ContentBlockParam.ofText(TextBlockParam.builder().text(t.text()).build());
            case LlmMessage.ToolUse u -> {
                ToolUseBlockParam.Input.Builder input = ToolUseBlockParam.Input.builder();
                if (u.input() != null && u.input().isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> it = u.input().fields();
                    while (it.hasNext()) {
                        Map.Entry<String, JsonNode> e = it.next();
                        input.putAdditionalProperty(e.getKey(), JsonValue.fromJsonNode(e.getValue()));
                    }
                }
                yield ContentBlockParam.ofToolUse(
                        ToolUseBlockParam.builder().id(u.id()).name(u.name()).input(input.build()).build());
            }
            case LlmMessage.ToolResult r -> ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                    .toolUseId(r.toolUseId())
                    .content(r.content())
                    .isError(r.error())
                    .build());
        };
    }

    Tool toTool(ToolSpec spec) {
        Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
        JsonNode properties = spec.inputSchema().path("properties");
        Iterator<Map.Entry<String, JsonNode>> it = properties.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            props.putAdditionalProperty(e.getKey(), JsonValue.fromJsonNode(e.getValue()));
        }
        List<String> required = new ArrayList<>();
        spec.inputSchema().path("required").forEach(n -> required.add(n.asText()));
        Tool.InputSchema.Builder schema = Tool.InputSchema.builder().properties(props.build());
        if (!required.isEmpty()) {
            schema.required(required);
        }
        return Tool.builder().name(spec.name()).description(spec.description()).inputSchema(schema.build()).build();
    }

    /** SDK JSON value to a Jackson tree; anything that is not an object becomes an empty object. */
    JsonNode toJson(JsonValue value) {
        try {
            JsonNode node = value.convert(JsonNode.class);
            return node != null && node.isObject() ? node : JsonNodeFactory.instance.objectNode();
        } catch (RuntimeException e) {
            log.warn("Could not read tool arguments from the model: {}", e.toString());
            return JsonNodeFactory.instance.objectNode();
        }
    }
}
