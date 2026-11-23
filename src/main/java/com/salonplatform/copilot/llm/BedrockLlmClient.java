package com.salonplatform.copilot.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salonplatform.config.CopilotProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultStatus;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * Amazon Bedrock (Converse API) adapter. Credentials come from the default AWS chain: the EC2 instance role in
 * production, an SSO profile locally. The model is a setting, so models can be compared without code changes.
 */
@Slf4j
@Component
@ConditionalOnExpression("${app.copilot.enabled:false} and '${app.copilot.provider:bedrock}' == 'bedrock'")
public class BedrockLlmClient implements LlmClient {

    private final BedrockRuntimeAsyncClient client;
    private final CopilotProperties props;
    private final ObjectMapper mapper;

    public BedrockLlmClient(CopilotProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.client = BedrockRuntimeAsyncClient.builder()
                .region(Region.of(props.getRegion()))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofMinutes(3))
                        .build())
                .build();
    }

    /** Accumulates one streamed tool call, whose JSON arguments arrive in pieces. */
    private static final class ToolAcc {
        final String id;
        final String name;
        final StringBuilder json = new StringBuilder();

        ToolAcc(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @Override
    public Turn stream(String system, List<LlmMessage> messages, List<ToolSpec> tools, Consumer<String> onText) {
        ConverseStreamRequest.Builder request = ConverseStreamRequest.builder()
                .modelId(props.resolvedModelId())
                .system(SystemContentBlock.builder().text(system).build())
                .messages(messages.stream().map(this::toMessage).toList())
                .inferenceConfig(InferenceConfiguration.builder().maxTokens(props.getMaxTokens()).build());
        if (!tools.isEmpty()) {
            request.toolConfig(ToolConfiguration.builder()
                    .tools(tools.stream().map(this::toTool).toList())
                    .build());
        }

        StringBuilder text = new StringBuilder();
        Map<Integer, ToolAcc> toolBlocks = new TreeMap<>();
        String[] stopReason = {"unknown"};
        int[] usage = {0, 0};

        ConverseStreamResponseHandler handler = ConverseStreamResponseHandler.builder()
                .subscriber(ConverseStreamResponseHandler.Visitor.builder()
                        .onContentBlockStart(e -> {
                            if (e.start() != null && e.start().toolUse() != null) {
                                toolBlocks.put(e.contentBlockIndex(),
                                        new ToolAcc(e.start().toolUse().toolUseId(), e.start().toolUse().name()));
                            }
                        })
                        .onContentBlockDelta(e -> {
                            if (e.delta() == null) return;
                            if (e.delta().text() != null) {
                                text.append(e.delta().text());
                                onText.accept(e.delta().text());
                            } else if (e.delta().toolUse() != null) {
                                ToolAcc acc = toolBlocks.get(e.contentBlockIndex());
                                if (acc != null && e.delta().toolUse().input() != null) {
                                    acc.json.append(e.delta().toolUse().input());
                                }
                            }
                        })
                        .onMessageStop(e -> stopReason[0] = e.stopReasonAsString())
                        .onMetadata(e -> {
                            if (e.usage() != null) {
                                usage[0] = e.usage().inputTokens() == null ? 0 : e.usage().inputTokens();
                                usage[1] = e.usage().outputTokens() == null ? 0 : e.usage().outputTokens();
                            }
                        })
                        .build())
                .build();

        try {
            client.converseStream(request.build(), handler).join();
        } catch (CompletionException e) {
            throw toLlmException(e.getCause() == null ? e : e.getCause());
        } catch (RuntimeException e) {
            throw toLlmException(e);
        }

        List<LlmMessage.Block> blocks = new ArrayList<>();
        if (!text.isEmpty()) {
            blocks.add(new LlmMessage.Text(text.toString()));
        }
        for (ToolAcc acc : toolBlocks.values()) {
            blocks.add(new LlmMessage.ToolUse(acc.id, acc.name, parseArguments(acc.json.toString())));
        }
        return new Turn(blocks, stopReason[0], usage[0], usage[1]);
    }

    private JsonNode parseArguments(String json) {
        if (json == null || json.isBlank()) {
            return JsonNodeFactory.instance.objectNode();
        }
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            log.warn("Model sent tool arguments that are not valid JSON: {}", json, e);
            return JsonNodeFactory.instance.objectNode();
        }
    }

    private LlmException toLlmException(Throwable cause) {
        log.warn("Bedrock call failed: {}", cause.toString());
        String msg = String.valueOf(cause.getMessage());
        if (msg.contains("not available for this account") || msg.contains("use case details")) {
            return new LlmException("The AI model is not enabled for this AWS account yet.", cause);
        }
        if (msg.contains("Too many tokens") || msg.contains("Throttling")) {
            return new LlmException("The AI model is busy or over its quota. Please try again shortly.", cause);
        }
        return new LlmException("The AI model could not be reached. Please try again.", cause);
    }

    private Message toMessage(LlmMessage m) {
        return Message.builder()
                .role(m.role() == LlmMessage.Role.USER ? ConversationRole.USER : ConversationRole.ASSISTANT)
                .content(m.blocks().stream().map(this::toContent).toList())
                .build();
    }

    private ContentBlock toContent(LlmMessage.Block block) {
        return switch (block) {
            case LlmMessage.Text t -> ContentBlock.fromText(t.text());
            case LlmMessage.ToolUse u -> ContentBlock.builder()
                    .toolUse(ToolUseBlock.builder()
                            .toolUseId(u.id())
                            .name(u.name())
                            .input(toDocument(u.input()))
                            .build())
                    .build();
            case LlmMessage.ToolResult r -> ContentBlock.builder()
                    .toolResult(ToolResultBlock.builder()
                            .toolUseId(r.toolUseId())
                            .content(ToolResultContentBlock.fromText(r.content()))
                            .status(r.error() ? ToolResultStatus.ERROR : ToolResultStatus.SUCCESS)
                            .build())
                    .build();
        };
    }

    private Tool toTool(ToolSpec spec) {
        return Tool.builder()
                .toolSpec(ToolSpecification.builder()
                        .name(spec.name())
                        .description(spec.description())
                        .inputSchema(ToolInputSchema.fromJson(toDocument(spec.inputSchema())))
                        .build())
                .build();
    }

    /** Jackson tree to the SDK's JSON document type. */
    static Document toDocument(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return Document.fromNull();
        }
        if (node.isObject()) {
            Map<String, Document> map = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                map.put(e.getKey(), toDocument(e.getValue()));
            }
            return Document.fromMap(map);
        }
        if (node.isArray()) {
            List<Document> list = new ArrayList<>();
            node.forEach(n -> list.add(toDocument(n)));
            return Document.fromList(list);
        }
        if (node.isBoolean()) {
            return Document.fromBoolean(node.booleanValue());
        }
        if (node.isIntegralNumber()) {
            return Document.fromNumber(node.longValue());
        }
        if (node.isNumber()) {
            return Document.fromNumber(node.decimalValue());
        }
        return Document.fromString(node.asText());
    }

    /** SDK document back to a Jackson tree (used by tests and for inspecting model-sent arguments). */
    static JsonNode fromDocument(Document doc) {
        JsonNodeFactory f = JsonNodeFactory.instance;
        if (doc == null || doc.isNull()) return f.nullNode();
        if (doc.isMap()) {
            ObjectNode o = f.objectNode();
            doc.asMap().forEach((k, v) -> o.set(k, fromDocument(v)));
            return o;
        }
        if (doc.isList()) {
            ArrayNode a = f.arrayNode();
            doc.asList().forEach(v -> a.add(fromDocument(v)));
            return a;
        }
        if (doc.isBoolean()) return f.booleanNode(doc.asBoolean());
        if (doc.isNumber()) return f.numberNode(doc.asNumber().bigDecimalValue());
        return f.textNode(doc.asString());
    }
}
