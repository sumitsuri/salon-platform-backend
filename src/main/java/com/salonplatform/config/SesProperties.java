package com.salonplatform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.ses")
public class SesProperties {

    private boolean enabled = false;

    /** Verified SES sender, e.g. noreply@antrahq.com */
    private String fromAddress = "";

    private String region = "ap-south-1";

    public boolean isConfigured() {
        return enabled && fromAddress != null && !fromAddress.isBlank();
    }
}
