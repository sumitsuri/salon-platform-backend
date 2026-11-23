package com.salonplatform.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salonplatform.config.CopilotProperties;
import com.salonplatform.copilot.llm.AnthropicLlmClient;
import com.salonplatform.copilot.llm.BedrockLlmClient;
import com.salonplatform.copilot.llm.LlmClient;
import com.salonplatform.copilot.llm.ScriptedLlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/** Which model (if any) gets wired for each setting, without starting the whole app. */
class CopilotWiringTest {

    @Configuration
    @EnableConfigurationProperties(CopilotProperties.class)
    @Import({AnthropicLlmClient.class, ScriptedLlmClient.class})
    static class Wiring {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Wiring.class);

    @Test
    void aMissingKeyMeansNoModelAndNoStartupFailure() {
        runner.withPropertyValues("app.copilot.enabled=true", "app.copilot.provider=anthropic",
                        "app.copilot.anthropic-api-key=")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).doesNotHaveBean(LlmClient.class);
                });
    }

    @Test
    void aKeyWiresTheAnthropicModel() {
        runner.withPropertyValues("app.copilot.enabled=true", "app.copilot.provider=anthropic",
                        "app.copilot.anthropic-api-key=sk-ant-api03-FAKEFAKEFAKEFAKEFAKE1234")
                .run(ctx -> assertThat(ctx).hasSingleBean(AnthropicLlmClient.class).doesNotHaveBean(ScriptedLlmClient.class));
    }

    @Test
    void theScriptedModelIsOnlyUsedWhenAskedFor() {
        runner.withPropertyValues("app.copilot.enabled=true", "app.copilot.provider=fake")
                .run(ctx -> assertThat(ctx).hasSingleBean(ScriptedLlmClient.class).doesNotHaveBean(AnthropicLlmClient.class));
    }

    @Test
    void whenTheFeatureIsOffNothingIsWiredEvenWithAKey() {
        runner.withPropertyValues("app.copilot.enabled=false", "app.copilot.provider=anthropic",
                        "app.copilot.anthropic-api-key=sk-ant-api03-FAKEFAKEFAKEFAKEFAKE1234")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(LlmClient.class));
    }

    @Test
    void theBedrockModelIsNotWiredForTheAnthropicProvider() {
        runner.withPropertyValues("app.copilot.enabled=true", "app.copilot.provider=anthropic",
                        "app.copilot.anthropic-api-key=sk-ant-api03-FAKEFAKEFAKEFAKEFAKE1234")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(BedrockLlmClient.class));
    }
}
