package com.salonplatform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Settings for the owner-facing AI assistant. Off by default; enabled per environment. */
@Data
@Component
@ConfigurationProperties(prefix = "app.copilot")
public class CopilotProperties {
    private boolean enabled = false;
    /** {@code anthropic} (API key), {@code bedrock} (AWS), or {@code fake} (scripted model for local development and tests). */
    private String provider = "bedrock";
    /** Blank picks the provider's Claude Sonnet 5.5 id; set it to compare other models. */
    private String modelId = "";
    private String region = "ap-south-1";
    /** API key from the Claude Console; used only when {@code provider=anthropic}. Never commit it. */
    private String anthropicApiKey = "";
    /** Thinking depth for the {@code anthropic} provider: low, medium, high, or blank for the model's default. */
    private String effort = "medium";
    /** Per model turn, thinking included. Too low cuts answers off mid-sentence; unused budget costs nothing. */
    private int maxTokens = 12000;
    /** How many model-to-tool round trips one question may take. */
    private int maxToolRounds = 8;
    /** How many times an answer that hit the output limit is automatically continued. */
    private int maxContinuations = 3;
    /** Largest tool result handed to the model, in characters (about 4 per token). */
    private int maxToolResultChars = 40_000;
    private int maxMessages = 20;
    private int maxMessageChars = 4000;
    private int dailyRequestsPerTenant = 300;
    /**
     * Brand slugs to switch the assistant on for at startup (never switches anyone off). This is how a protected
     * production brand is enabled, since platform admin cannot modify it.
     */
    private java.util.List<String> enableForSlugs = new java.util.ArrayList<>();

    /** The Bedrock id carries a routing prefix; the direct API uses the bare model name. */
    public String resolvedModelId() {
        if (modelId != null && !modelId.isBlank()) {
            return modelId.trim();
        }
        return "anthropic".equals(provider) ? "claude-sonnet-5-5" : "global.anthropic.claude-sonnet-5-5";
    }
}
