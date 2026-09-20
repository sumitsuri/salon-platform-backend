package com.salonplatform.google;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Lets local backends call Google Places via production (prod API key is IP-restricted to EC2).
 */
@RestController
@RequestMapping("/api/v1/internal/places")
@RequiredArgsConstructor
public class GooglePlacesInternalController {

    private static final String SECRET_HEADER = "X-Places-Proxy-Secret";

    private final GooglePlacesProperties properties;
    private final GooglePlacesDirectGateway directGateway;

    @PostMapping("/search-nearby")
    public List<GooglePlaceSnapshot> searchNearby(
            @RequestHeader(value = SECRET_HEADER, required = false) String secret,
            @RequestBody GooglePlacesProdProxy.NearbyProxyRequest request) {
        assertProxySecret(secret);
        return directGateway.searchNearby(
                request.lat(),
                request.lng(),
                request.radiusMeters(),
                request.maxResults(),
                request.includedTypes());
    }

    @PostMapping("/search-text")
    public List<GooglePlaceSnapshot> searchText(
            @RequestHeader(value = SECRET_HEADER, required = false) String secret,
            @RequestBody GooglePlacesProdProxy.TextProxyRequest request) {
        assertProxySecret(secret);
        return directGateway.searchText(
                request.textQuery(),
                request.lat(),
                request.lng(),
                request.radiusMeters(),
                request.maxResults());
    }

    private void assertProxySecret(String provided) {
        String expected = properties.getInternalProxySecret();
        if (expected == null || expected.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (provided == null || !constantTimeEquals(expected, provided)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid Places proxy secret");
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] left = a.getBytes(StandardCharsets.UTF_8);
        byte[] right = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }
}
