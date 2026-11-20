package com.salonplatform.copilot.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.document.Document;

import static org.assertj.core.api.Assertions.assertThat;

class BedrockDocumentConversionTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void toolSchemasAndArgumentsSurviveTheRoundTripToTheSdkDocumentType() throws Exception {
        JsonNode original = mapper.readTree("""
                {"type":"object","properties":{"startDate":{"type":"string"},
                 "branchIds":{"type":"array","items":{"type":"string"}},
                 "limit":{"type":"integer"},"ratio":{"type":"number"},"flag":{"type":"boolean"}},
                 "required":["startDate"],"nothing":null}
                """);

        Document doc = BedrockLlmClient.toDocument(original);
        JsonNode back = BedrockLlmClient.fromDocument(doc);

        assertThat(doc.isMap()).isTrue();
        assertThat(back.get("required").get(0).asText()).isEqualTo("startDate");
        assertThat(back.get("properties").get("branchIds").get("items").get("type").asText()).isEqualTo("string");
        assertThat(back.get("nothing").isNull()).isTrue();
    }

    @Test
    void numbersKeepTheirValue() throws Exception {
        JsonNode back = BedrockLlmClient.fromDocument(BedrockLlmClient.toDocument(
                mapper.readTree("{\"whole\":42,\"fraction\":0.25,\"flag\":true}")));
        assertThat(back.get("whole").decimalValue().intValue()).isEqualTo(42);
        assertThat(back.get("fraction").decimalValue().doubleValue()).isEqualTo(0.25);
        assertThat(back.get("flag").asBoolean()).isTrue();
    }
}
