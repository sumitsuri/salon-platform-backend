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
    /** {@code bedrock} for the real model, {@code fake} for a scripted model used in local development and tests. */
    private String provider = "bedrock";
    private String modelId = "global.anthropic.claude-sonnet-5-5";
    private String region = "ap-south-1";
    private int maxTokens = 2000;
    /** How many model-to-tool round trips one question may take. */
    private int maxToolRounds = 6;
    private int maxMessages = 20;
    private int maxMessageChars = 4000;
    private int dailyRequestsPerTenant = 300;
}
