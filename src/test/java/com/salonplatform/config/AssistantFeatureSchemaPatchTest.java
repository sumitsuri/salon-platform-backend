package com.salonplatform.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AssistantFeatureSchemaPatchTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CopilotProperties props = new CopilotProperties();

    @Test
    void addsTheColumnAndSwitchesOnOnlyTheListedBrands() {
        props.setEnableForSlugs(List.of(" Mystic-Wellness ", "", "  "));

        new AssistantFeatureSchemaPatch(jdbc, props).run(new DefaultApplicationArguments());

        verify(jdbc).execute(contains("ADD COLUMN IF NOT EXISTS assistant_enabled"));
        verify(jdbc).update(contains("assistant_enabled = true"), eq("mystic-wellness"));
    }

    @Test
    void anEmptyListSwitchesNobodyOn() {
        new AssistantFeatureSchemaPatch(jdbc, props).run(new DefaultApplicationArguments());

        verify(jdbc).execute(contains("ADD COLUMN IF NOT EXISTS assistant_enabled"));
        verify(jdbc, never()).update(contains("assistant_enabled = true"), anyString());
    }

    @Test
    void theSwitchOnQueryNeverTurnsAnyoneOff() {
        props.setEnableForSlugs(List.of("mystic-wellness"));

        new AssistantFeatureSchemaPatch(jdbc, props).run(new DefaultApplicationArguments());

        verify(jdbc, never()).update(contains("assistant_enabled = false WHERE lower"), anyString());
    }
}
