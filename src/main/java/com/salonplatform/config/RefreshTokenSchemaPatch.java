package com.salonplatform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Sessions no longer expire (except for platform staff), so {@code refresh_tokens.expires_at} must allow NULL —
 * Hibernate's ddl-auto=update adds the new device columns but never relaxes an existing NOT NULL.
 * Also lifts the old 20-day limit from every live session that belongs to a non-platform user. Idempotent.
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenSchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE refresh_tokens ALTER COLUMN expires_at DROP NOT NULL");
            // Includes sessions whose 20 days already lapsed but were never cleaned up: their browsers still hold
            // the token, so those people are quietly signed back in on their next visit.
            int lifted = jdbcTemplate.update("""
                    UPDATE refresh_tokens t
                    SET expires_at = NULL
                    FROM users u
                    WHERE u.id = t.user_id
                      AND t.expires_at IS NOT NULL
                      AND u.role NOT IN ('PLATFORM_SUPER_ADMIN', 'SALES_EXECUTIVE')
                    """);
            if (lifted > 0) {
                log.info("Lifted the idle expiry from {} existing session(s)", lifted);
            }
        } catch (Exception e) {
            log.warn("Refresh token schema patch skipped or partial: {}", e.getMessage());
        }
    }
}
