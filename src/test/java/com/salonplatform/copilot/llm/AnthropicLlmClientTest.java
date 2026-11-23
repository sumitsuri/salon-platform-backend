package com.salonplatform.copilot.llm;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.Tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salonplatform.config.CopilotProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** No network: only the conversions between our message types and the SDK's. */
class AnthropicLlmClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private AnthropicLlmClient client(String key) {
        CopilotProperties props = new CopilotProperties();
        props.setProvider("anthropic");
        props.setAnthropicApiKey(key);
        return new AnthropicLlmClient(props, mapper);
    }

    @Test
    void refusesToStartWithoutAnApiKeySoTheMistakeIsLoudNotSilent() {
        assertThatThrownBy(() -> client(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }

    @Test
    void aToolSchemaKeepsItsPropertiesAndRequiredFields() throws Exception {
        JsonNode schema = mapper.readTree("""
                {"type":"object","properties":{"startA":{"type":"string"},
                 "branchIds":{"type":"array","items":{"type":"string"}}},"required":["startA"]}
                """);

        Tool tool = client("sk-test").toTool(new LlmClient.ToolSpec("compare_periods", "Compare two periods", schema));

        assertThat(tool.name()).isEqualTo("compare_periods");
        assertThat(tool.inputSchema().required()).contains(List.of("startA"));
        assertThat(tool.inputSchema().properties().orElseThrow()._additionalProperties())
                .containsKeys("startA", "branchIds");
    }

    @Test
    void aToolWithNoArgumentsStillBuilds() throws Exception {
        JsonNode schema = mapper.readTree("{\"type\":\"object\",\"properties\":{}}");
        Tool tool = client("sk-test").toTool(new LlmClient.ToolSpec("list_branches", "List branches", schema));
        assertThat(tool.name()).isEqualTo("list_branches");
    }

    @Test
    void aToolCallAndItsResultRoundTripAsContentBlocks() throws Exception {
        AnthropicLlmClient c = client("sk-test");
        LlmMessage assistant = new LlmMessage(LlmMessage.Role.ASSISTANT, List.of(
                new LlmMessage.Text("Checking."),
                new LlmMessage.ToolUse("toolu_1", "sales_overview", mapper.readTree("{\"startDate\":\"2026-11-01\"}"))));
        LlmMessage results = new LlmMessage(LlmMessage.Role.USER, List.of(
                new LlmMessage.ToolResult("toolu_1", "{\"revenue\":125000}", false)));

        MessageParam a = c.toMessage(assistant);
        MessageParam r = c.toMessage(results);

        assertThat(a.assistant()).isPresent();
        assertThat(r.user()).isPresent();
        assertThat(a.toString()).contains("toolu_1").contains("sales_overview").contains("2026-11-01");
        assertThat(r.toString()).contains("toolu_1").contains("125000");
    }

    @Test
    void modelArgumentsComeBackAsAJsonObject() {
        AnthropicLlmClient c = client("sk-test");
        JsonNode node = c.toJson(JsonValue.from(java.util.Map.of("branchIds", List.of("a", "b"), "limit", 5)));
        assertThat(node.get("branchIds").get(1).asText()).isEqualTo("b");
        assertThat(node.get("limit").asInt()).isEqualTo(5);
        assertThat(c.toJson(JsonValue.from("not an object")).isObject()).isTrue();
    }

    @Test
    void theDefaultModelFollowsTheProvider() {
        CopilotProperties p = new CopilotProperties();
        p.setProvider("anthropic");
        assertThat(p.resolvedModelId()).isEqualTo("claude-sonnet-5-5");
        p.setProvider("bedrock");
        assertThat(p.resolvedModelId()).isEqualTo("global.anthropic.claude-sonnet-5-5");
        p.setModelId("claude-haiku-5-5");
        assertThat(p.resolvedModelId()).isEqualTo("claude-haiku-5-5");
    }
}
