package com.salonplatform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiry-ms}") long accessTokenExpiryMs,
            @Value("${app.jwt.refresh-token-expiry-ms}") long refreshTokenExpiryMs) {
        byte[] keyBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            keyBytes = java.util.Arrays.copyOf(keyBytes, 32);
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpiryMs = accessTokenExpiryMs;
        this.refreshTokenExpiryMs = refreshTokenExpiryMs;
    }

    public String generateAccessToken(Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return buildToken(principal, accessTokenExpiryMs, "access", null);
    }

    public String generateAccessToken(UserPrincipal principal) {
        return buildToken(principal, accessTokenExpiryMs, "access", null);
    }

    /** Access token tied to a device session ({@code sid} claim) so the app can tell "this device" from the others. */
    public String generateAccessToken(UserPrincipal principal, UUID sessionId) {
        return buildToken(principal, accessTokenExpiryMs, "access", sessionId);
    }

    public String generateRefreshTokenValue() {
        return UUID.randomUUID().toString() + "-" + UUID.randomUUID();
    }

    public long getRefreshTokenExpiryMs() {
        return refreshTokenExpiryMs;
    }

    private String buildToken(UserPrincipal principal, long expiry, String type, UUID sessionId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiry);

        return Jwts.builder()
                .subject(principal.getEmail())
                .claim("userId", principal.getId().toString())
                .claim("tenantId", principal.getTenantId() != null ? principal.getTenantId().toString() : null)
                .claim("branchId", principal.getBranchId() != null ? principal.getBranchId().toString() : null)
                .claim("role", principal.getRole().name())
                .claim("name", principal.getName())
                .claim("type", type)
                .claim("sid", sessionId != null ? sessionId.toString() : null)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /** The device session an access token belongs to, or null for tokens issued before sessions were tracked. */
    public UUID getSessionId(String token) {
        try {
            Object sid = parseClaims(token).get("sid");
            return sid != null ? UUID.fromString(sid.toString()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
