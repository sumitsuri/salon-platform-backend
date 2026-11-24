package com.salonplatform.copilot.feedback;

import com.salonplatform.security.SecurityUtils;
import com.salonplatform.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Thumbs up or down on an answer, and whether the microphone understood what was said. It stores ratings and the
 * circumstances (typed or spoken, which engine, which language), never the question or the answer, so the quality of
 * the assistant and of speech recognition can be tracked over time without keeping anyone's business data.
 */
@Service
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class AssistantFeedbackService implements ApplicationRunner {

    public static final Set<String> REASONS = Set.of("WRONG_NUMBERS", "WRONG_LANGUAGE", "NOT_ANSWERED", "TOO_LONG", "TOO_SLOW", "OTHER");
    private static final Set<String> RATINGS = Set.of("UP", "DOWN");
    private static final Set<String> MODES = Set.of("TYPED", "VOICE");
    private static final Set<String> LANGS = Set.of("en-IN", "hi-IN", "mr-IN", "gu-IN", "pa-IN", "bn-IN", "ta-IN", "te-IN", "kn-IN", "ml-IN");
    private static final Set<String> ENGINES = Set.of("browser", "sarvam", "openai", "fake");

    /**
     * @param messageId       the answer being rated (an id the browser made; it holds no content)
     * @param rating          UP or DOWN, or null when only the speech question was answered
     * @param reasons         why it was thumbs down
     * @param inputMode       TYPED or VOICE: how the question was asked
     * @param voiceEngine     browser, sarvam, openai (only for VOICE)
     * @param voiceLang       the language chosen for the microphone (only for VOICE)
     * @param heardCorrectly  whether the words on screen were what the person said (only for VOICE)
     */
    public record Feedback(String messageId, String rating, List<String> reasons, String inputMode, String voiceEngine,
                           String voiceLang, Boolean heardCorrectly) {}

    private final JdbcTemplate ddl;
    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ddl.execute("""
                    CREATE TABLE IF NOT EXISTS assistant_feedback (
                        id             UUID PRIMARY KEY,
                        created_at     TIMESTAMPTZ NOT NULL,
                        updated_at     TIMESTAMPTZ NOT NULL,
                        tenant_id      VARCHAR(64) NOT NULL,
                        user_id        VARCHAR(64) NOT NULL,
                        message_id     VARCHAR(64) NOT NULL,
                        rating         VARCHAR(8),
                        reasons        VARCHAR(160),
                        input_mode     VARCHAR(8) NOT NULL,
                        voice_engine   VARCHAR(16),
                        voice_lang     VARCHAR(8),
                        heard_correctly BOOLEAN
                    )""");
            ddl.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_assistant_feedback_user_message ON assistant_feedback (user_id, message_id)");
            ddl.execute("CREATE INDEX IF NOT EXISTS idx_assistant_feedback_time ON assistant_feedback (created_at)");
            log.info("Assistant feedback table ready");
        } catch (Exception e) {
            log.warn("Assistant feedback schema patch skipped or partial: {}", e.getMessage());
        }
    }

    /** Saves or updates this person's feedback on one answer. Rating and the speech check can arrive separately. */
    public void submit(Feedback f) {
        SecurityUtils.assertBrandAdminOrAbove();
        String message = f.messageId() == null ? "" : f.messageId().trim();
        if (message.isEmpty() || message.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing answer id.");
        }
        if (f.rating() != null && !RATINGS.contains(f.rating())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating must be UP or DOWN.");
        }
        if (f.rating() == null && f.heardCorrectly() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing to save.");
        }
        String mode = f.inputMode() == null ? "TYPED" : f.inputMode();
        if (!MODES.contains(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown input mode.");
        }
        boolean voice = "VOICE".equals(mode);
        String engine = voice && f.voiceEngine() != null && ENGINES.contains(f.voiceEngine()) ? f.voiceEngine() : null;
        String lang = voice && f.voiceLang() != null && LANGS.contains(f.voiceLang()) ? f.voiceLang() : null;
        String reasons = "DOWN".equals(f.rating()) && f.reasons() != null
                ? f.reasons().stream().filter(REASONS::contains).distinct().collect(Collectors.joining(","))
                : "";
        UserPrincipal user = SecurityUtils.currentUser();
        jdbc.update("""
                INSERT INTO assistant_feedback (id, created_at, updated_at, tenant_id, user_id, message_id, rating, reasons,
                    input_mode, voice_engine, voice_lang, heard_correctly)
                VALUES (:id, now(), now(), :tenant, :user, :message, :rating, :reasons, :mode, :engine, :lang, :heard)
                ON CONFLICT (user_id, message_id) DO UPDATE SET
                    updated_at      = now(),
                    rating          = COALESCE(EXCLUDED.rating, assistant_feedback.rating),
                    reasons         = CASE WHEN EXCLUDED.rating IS NULL THEN assistant_feedback.reasons ELSE EXCLUDED.reasons END,
                    heard_correctly = COALESCE(EXCLUDED.heard_correctly, assistant_feedback.heard_correctly)""",
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("tenant", String.valueOf(user.getTenantId()))
                        .addValue("user", String.valueOf(user.getId()))
                        .addValue("message", message)
                        .addValue("rating", f.rating())
                        .addValue("reasons", reasons)
                        .addValue("mode", mode)
                        .addValue("engine", engine)
                        .addValue("lang", lang)
                        .addValue("heard", f.heardCorrectly()));
    }

    /** For AntraHQ: how answers and speech recognition are rated, by engine and language, per day, and why answers were rejected. */
    public Map<String, Object> summary(int days) {
        SecurityUtils.assertPlatformAdmin();
        var p = new MapSqlParameterSource("days", days);
        List<Map<String, Object>> byMode = jdbc.queryForList("""
                SELECT input_mode AS "inputMode", COALESCE(voice_engine, '') AS "engine", COALESCE(voice_lang, '') AS "lang",
                       COUNT(*) FILTER (WHERE rating IS NOT NULL)            AS "rated",
                       COUNT(*) FILTER (WHERE rating = 'UP')                 AS "up",
                       COUNT(*) FILTER (WHERE rating = 'DOWN')               AS "down",
                       COUNT(*) FILTER (WHERE heard_correctly IS NOT NULL)   AS "speechChecked",
                       COUNT(*) FILTER (WHERE heard_correctly = TRUE)        AS "heardRight",
                       COUNT(*) FILTER (WHERE heard_correctly = FALSE)       AS "heardWrong"
                FROM assistant_feedback
                WHERE created_at >= now() - (:days * interval '1 day')
                GROUP BY 1, 2, 3 ORDER BY 1, 2, 3""", p);
        List<Map<String, Object>> daily = jdbc.queryForList("""
                SELECT to_char(created_at AT TIME ZONE 'Asia/Kolkata', 'YYYY-MM-DD') AS "day",
                       COUNT(*) FILTER (WHERE rating = 'UP')                 AS "up",
                       COUNT(*) FILTER (WHERE rating = 'DOWN')               AS "down",
                       COUNT(*) FILTER (WHERE heard_correctly = TRUE)        AS "heardRight",
                       COUNT(*) FILTER (WHERE heard_correctly = FALSE)       AS "heardWrong"
                FROM assistant_feedback
                WHERE created_at >= now() - (:days * interval '1 day')
                GROUP BY 1 ORDER BY 1""", p);
        List<Map<String, Object>> reasons = jdbc.queryForList("""
                SELECT r AS "reason", COUNT(*) AS "count"
                FROM assistant_feedback, unnest(string_to_array(reasons, ',')) AS r
                WHERE rating = 'DOWN' AND r <> '' AND created_at >= now() - (:days * interval '1 day')
                GROUP BY r ORDER BY COUNT(*) DESC""", p);
        return Map.of("days", days, "byInput", byMode, "daily", daily, "downReasons", reasons);
    }

}
