package com.salonplatform.copilot.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonTrimmerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void smallResultsPassThroughUntouched() {
        String json = JsonTrimmer.toTrimmedJson(mapper, Map.of("revenue", 1200), 1000);
        assertThat(json).isEqualTo("{\"revenue\":1200}");
    }

    @Test
    void longListsAreCutToFitAndTheCutIsStated() throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            items.add(Map.of("day", "2026-01-" + i, "revenue", i * 1000));
        }
        String json = JsonTrimmer.toTrimmedJson(mapper, Map.of("series", items), 1200);

        assertThat(json.length()).isLessThanOrEqualTo(1200);
        JsonNode series = mapper.readTree(json).get("series");
        assertThat(series.get(series.size() - 1).get("_truncated").asText()).contains("more items not shown");
    }

    @Test
    void anImpossiblySmallBudgetReturnsAnErrorInsteadOfBrokenJson() throws Exception {
        List<String> big = new ArrayList<>();
        for (int i = 0; i < 50; i++) big.add("x".repeat(500));
        String json = JsonTrimmer.toTrimmedJson(mapper, Map.of("a", big), 200);
        assertThat(mapper.readTree(json).has("error")).isTrue();
    }
}
