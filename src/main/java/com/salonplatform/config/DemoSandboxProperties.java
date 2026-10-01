package com.salonplatform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.demo-sandbox")
public class DemoSandboxProperties {

    /**
     * Phones (10-digit Indian mobiles) that still receive real WhatsApp/SMS from SIMULATE brands —
     * team devices used to film a real receipt arriving. Everyone else is simulated.
     */
    private List<String> allowedPhones = new ArrayList<>();
}
