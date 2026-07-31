package com.salonplatform.reviews.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.reviews")
@Getter
@Setter
public class ReviewsProperties {
    private String tokenSecret;
    private int tokenExpiryDays = 7;
    private String publicFrontendBaseUrl = "http://localhost:3000";
}
