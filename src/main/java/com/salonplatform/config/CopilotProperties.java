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
    /** Questions one person may ask per day (a business's people share {@code dailyRequestsPerTenant}). */
    private int dailyRequestsPerUser = 60;
    /** Model spend per business per day and per month, in rupees; the gateway refuses further questions once reached. */
    private double dailySpendInrPerTenant = 300;
    private double monthlySpendInrPerTenant = 3000;
    /** Refused attempts (off-topic, secrets, injection) within an hour before a person is paused, and for how long. */
    private int violationsBeforeCooldown = 5;
    private int cooldownMinutes = 30;
    /** Ask a small, cheap model whether an unclear question is about the business, instead of refusing it outright. */
    private boolean classifierEnabled = true;
    /** Blank picks the provider's Haiku 5.5. */
    private String classifierModelId = "";
    /**
     * Brand slugs to switch the assistant on for at startup (never switches anyone off). This is how a protected
     * production brand is enabled, since platform admin cannot modify it.
     */
    private java.util.List<String> enableForSlugs = new java.util.ArrayList<>();

    public String resolvedClassifierModelId() {
        if (classifierModelId != null && !classifierModelId.isBlank()) {
            return classifierModelId.trim();
        }
        return "anthropic".equals(provider) ? "claude-haiku-5-5" : "global.anthropic.claude-haiku-5-5";
    }

    /** The Bedrock id carries a routing prefix; the direct API uses the bare model name. */
    public String resolvedModelId() {
        if (modelId != null && !modelId.isBlank()) {
            return modelId.trim();
        }
        return "anthropic".equals(provider) ? "claude-sonnet-5-5" : "global.anthropic.claude-sonnet-5-5";
    }
}
