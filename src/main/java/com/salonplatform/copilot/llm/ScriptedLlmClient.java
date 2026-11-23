package com.salonplatform.copilot.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A scripted stand-in for a real model, for local development and tests only ({@code app.copilot.provider=fake}).
 * It picks a tool from keywords in the question, then answers by quoting what the tool returned. It does no
 * reasoning, and says so in every answer, so it can never be mistaken for real analysis.
 */
@Component
@ConditionalOnProperty(prefix = "app.copilot", name = "provider", havingValue = "fake")
public class ScriptedLlmClient implements LlmClient {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private final ObjectMapper mapper;

    public ScriptedLlmClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Turn stream(String system, List<LlmMessage> messages, List<ToolSpec> tools, Consumer<String> onText) {
        LlmMessage last = messages.get(messages.size() - 1);
        boolean hasToolResults = last.blocks().stream().anyMatch(LlmMessage.ToolResult.class::isInstance);
        if (hasToolResults) {
            return answer(last, onText);
        }
        String question = last.blocks().stream()
                .filter(LlmMessage.Text.class::isInstance)
                .map(b -> ((LlmMessage.Text) b).text())
                .findFirst().orElse("")
                .toLowerCase(Locale.ROOT);
        return callTool(question, onText);
    }

    private Turn callTool(String q, Consumer<String> onText) {
        LocalDate end = LocalDate.now(IST);
        LocalDate start = end.minusDays(29);
        ObjectNode args = mapper.createObjectNode();
        String tool;
        if (q.contains("busy") || q.contains("peak") || q.contains("crowd")) {
            tool = "busy_hours";
        } else if (q.contains("compare") || q.contains("drop") || q.contains("decline") || q.contains("down")) {
            tool = "compare_periods";
            args.put("startA", end.minusDays(59).toString());
            args.put("endA", end.minusDays(30).toString());
            args.put("startB", start.toString());
            args.put("endB", end.toString());
            return toolTurn(tool, args, "Let me compare the last 30 days with the 30 days before.", onText);
        } else if (q.contains("stock") || q.contains("inventory") || q.contains("product")) {
            tool = "inventory_overview";
        } else if (q.contains("attendance") || q.contains("late") || q.contains("present")) {
            tool = "attendance_overview";
        } else if (q.contains("profit") || q.contains("expense") || q.contains("p&l")) {
            tool = "profit_and_loss";
        } else if (q.contains("service")) {
            tool = "service_mix";
        } else if (q.contains("salary") || q.contains("scorecard") || q.contains("4x") || q.contains("target")) {
            tool = "staff_scorecard";
        } else if (q.contains("staff") || q.contains("stylist") || q.contains("employee")) {
            tool = "staff_sales";
        } else {
            tool = "sales_overview";
        }
        args.put("startDate", start.toString());
        args.put("endDate", end.toString());
        return toolTurn(tool, args, "Let me check the numbers.", onText);
    }

    private Turn toolTurn(String tool, ObjectNode args, String lead, Consumer<String> onText) {
        onText.accept(lead);
        return new Turn(List.of(
                new LlmMessage.Text(lead),
                new LlmMessage.ToolUse("fake-" + UUID.randomUUID(), tool, args)),
                "tool_use", 0, 0);
    }

    private Turn answer(LlmMessage toolResults, Consumer<String> onText) {
        StringBuilder out = new StringBuilder("> Scripted model (development only): it quotes the tool output and does no analysis.\n\n");
        for (LlmMessage.Block b : toolResults.blocks()) {
            if (b instanceof LlmMessage.ToolResult r) {
                String body = r.content();
                out.append(r.error() ? "The tool failed: " : "Here is what the data returned:\n\n```json\n")
                        .append(body.length() > 900 ? body.substring(0, 900) + " ..." : body)
                        .append(r.error() ? "\n" : "\n```\n");
            }
        }
        String text = out.toString();
        for (String chunk : text.split("(?<=\\s)")) {
            onText.accept(chunk);
        }
        return new Turn(List.of(new LlmMessage.Text(text)), "end_turn", 0, 0);
    }
}
