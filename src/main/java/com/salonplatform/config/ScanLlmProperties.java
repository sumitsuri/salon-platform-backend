package com.salonplatform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.scan.llm")
public class ScanLlmProperties {
    private boolean enabled = false;
    private String apiKey = "";
    private String model = "gpt-4o-mini";
    private int maxImages = 3;
    private String baseUrl = "https://api.openai.com/v1";
}
