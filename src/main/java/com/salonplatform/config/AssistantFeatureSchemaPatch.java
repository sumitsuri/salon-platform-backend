package com.salonplatform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adds the per-brand Ask Antra switch and turns it on for the brands named in {@code app.copilot.enable-for-slugs}.
 * It only ever switches brands on, so a later restart with a shorter list never turns anyone off, and a brand
 * switched off in platform admin is not silently re-enabled unless it is still listed.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class AssistantFeatureSchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final CopilotProperties props;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE tenants ADD COLUMN IF NOT EXISTS assistant_enabled BOOLEAN");
            jdbcTemplate.update("UPDATE tenants SET assistant_enabled = false WHERE assistant_enabled IS NULL");

            List<String> slugs = props.getEnableForSlugs().stream()
                    .map(s -> s == null ? "" : s.trim().toLowerCase())
                    .filter(s -> !s.isEmpty())
                    .toList();
            for (String slug : slugs) {
                int n = jdbcTemplate.update(
                        "UPDATE tenants SET assistant_enabled = true WHERE lower(slug) = ? AND assistant_enabled = false",
                        slug);
                log.info("Ask Antra: {} for brand '{}'", n > 0 ? "switched on" : "already on or brand not found", slug);
            }
        } catch (Exception e) {
            log.warn("Ask Antra feature schema patch skipped or partial: {}", e.getMessage());
        }
    }
}
