package com.salonplatform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.password-reset")
public class PasswordResetProperties {

    private int tokenExpiryMinutes = 60;

    private String frontendBaseUrl = "http://localhost:3000";
}
