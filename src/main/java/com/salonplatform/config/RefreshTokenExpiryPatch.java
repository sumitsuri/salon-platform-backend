package com.salonplatform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Extends active refresh tokens to the configured session window (15 days).
 * One-time uplift for sessions created under the previous 7-day policy.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenExpiryPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Value("${app.jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Timestamp minExpiry = Timestamp.from(Instant.now().plusMillis(refreshTokenExpiryMs));
            int updated = jdbcTemplate.update(
                    """
                    UPDATE refresh_tokens
                    SET expires_at = GREATEST(expires_at, ?)
                    WHERE revoked = false
                      AND expires_at > NOW()
                    """,
                    minExpiry);
            if (updated > 0) {
                log.info("Extended {} active refresh token(s) to at least {} ms from now", updated, refreshTokenExpiryMs);
            }
        } catch (Exception e) {
            log.warn("Refresh token expiry patch skipped or partial: {}", e.getMessage());
        }
    }
}
