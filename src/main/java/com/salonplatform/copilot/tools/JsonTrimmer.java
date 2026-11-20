package com.salonplatform.copilot.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;

/** Keeps tool results small enough for the model: long lists are cut and the cut is stated, never silent. */
public final class JsonTrimmer {

    private static final int[] ARRAY_LIMITS = {25, 12, 6, 3};

    private JsonTrimmer() {}

    /** Serialises {@code value}, shrinking long lists step by step until it fits in {@code maxChars}. */
    public static String toTrimmedJson(ObjectMapper mapper, Object value, int maxChars) {
        JsonNode tree = mapper.valueToTree(value);
        String json = tree.toString();
        for (int limit : ARRAY_LIMITS) {
            if (json.length() <= maxChars) {
                return json;
            }
            json = cap(tree.deepCopy(), limit).toString();
        }
        return json.length() <= maxChars
                ? json
                : "{\"error\":\"Result too large to show; ask for a shorter period or one branch.\"}";
    }

    static JsonNode cap(JsonNode node, int limit) {
        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            int total = arr.size();
            while (arr.size() > limit) {
                arr.remove(arr.size() - 1);
            }
            for (JsonNode child : arr) {
                cap(child, limit);
            }
            if (total > limit) {
                ObjectNode note = arr.addObject();
                note.put("_truncated", total - limit + " more items not shown");
            }
        } else if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                cap(it.next().getValue(), limit);
            }
        }
        return node;
    }
}
