package com.salonplatform.google;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GooglePlacesDirectGatewayTest {

    @Test
    void formatGooglePlacesError_maps429ToRateLimitMessage() {
        RestClientResponseException ex = new RestClientResponseException(
                "Too Many Requests", 429, "Too Many Requests", null, null, null);
        String msg = GooglePlacesDirectGateway.formatGooglePlacesError(ex);
        assertTrue(msg.contains("429"));
        assertTrue(msg.toLowerCase().contains("rate limit"));
    }
}
