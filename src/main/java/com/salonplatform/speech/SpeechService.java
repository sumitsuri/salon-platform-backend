package com.salonplatform.speech;

import com.salonplatform.aigateway.AiGateway;
import com.salonplatform.aigateway.ledger.AuditEvent;
import com.salonplatform.aigateway.ledger.GatewayLedger;
import com.salonplatform.aigateway.model.Subject;
import com.salonplatform.copilot.gateway.AskAntraProfile;
import com.salonplatform.domain.repository.TenantRepository;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns a short recording into text with whichever engine is switched on. Spoken words become an ordinary typed question
 * afterwards, so everything the assistant checks on typed text (secrets, scope, limits) still applies to them.
 *
 * <p>Audio is never stored or logged. What is recorded is who asked, how many seconds, which engine and what it cost,
 * so the spend counts towards the business's limit and the per-person allowance can be enforced.
 */
@Service
@Slf4j
public class SpeechService {

    static final Set<String> LANGUAGES = Set.of("en-IN", "hi-IN", "mr-IN", "gu-IN", "pa-IN", "bn-IN", "ta-IN", "te-IN", "kn-IN", "ml-IN");
    private static final String KIND = "SPEECH";
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    public record Result(String text, String engine) {}

    private final SpeechProperties props;
    private final ObjectProvider<SpeechToText> engine;
    private final TenantRepository tenants;
    private final AiGateway gateway;
    private final GatewayLedger ledger;
    private final JdbcTemplate jdbc;

    public SpeechService(SpeechProperties props, ObjectProvider<SpeechToText> engine, TenantRepository tenants,
                         AiGateway gateway, GatewayLedger ledger, JdbcTemplate jdbc) {
        this.props = props;
        this.engine = engine;
        this.tenants = tenants;
        this.gateway = gateway;
        this.ledger = ledger;
        this.jdbc = jdbc;
    }

    /** What the app shows: whether Antra's own speech engine is on, and which one (for the feedback metrics). */
    public Map<String, Object> status() {
        SpeechToText e = props.isEnabled() ? engine.getIfAvailable() : null;
        return e == null ? Map.of("server", false) : Map.of("server", true, "engine", e.id(), "maxSeconds", props.getMaxSeconds());
    }

    public Result transcribe(byte[] audio, String mimeType, String language, int seconds) {
        SecurityUtils.assertBrandAdminOrAbove();
        SpeechToText e = props.isEnabled() ? engine.getIfAvailable() : null;
        if (e == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Voice typing on our servers is not turned on.");
        }
        UUID tenantId = SecurityUtils.requireTenantId();
        if (!tenants.findById(tenantId).map(t -> t.isAssistantLicensed()).orElse(false)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The assistant is not enabled for your business yet.");
        }
        if (audio == null || audio.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "There was no sound in that recording.");
        }
        if (audio.length > props.getMaxBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "That recording is too long. Please keep it short.");
        }
        if (mimeType == null || !mimeType.toLowerCase().startsWith("audio/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That is not an audio recording.");
        }
        if (!LANGUAGES.contains(language)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose one of the listed languages.");
        }
        // The browser says how long it recorded; trust it only to cost the call, never to widen a limit.
        int billed = Math.max(1, Math.min(seconds, props.getMaxSeconds()));

        UserPrincipal user = SecurityUtils.currentUser();
        Subject subject = new Subject(String.valueOf(user.getTenantId()), String.valueOf(user.getId()), String.valueOf(user.getRole()));
        AiGateway.UsageReport usage = gateway.usage(subject, AskAntraProfile.NAME);
        var limits = usage.limits();
        if ((limits.dailySpendPerTenantInr() > 0 && usage.used().spendTenantToday().doubleValue() >= limits.dailySpendPerTenantInr())
                || (limits.monthlySpendPerTenantInr() > 0 && usage.used().spendTenantMonth().doubleValue() >= limits.monthlySpendPerTenantInr())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Your business has used today's assistant allowance. Please type your question, or try again tomorrow.");
        }
        if (secondsToday(String.valueOf(user.getId())) + billed > props.getDailySecondsPerUser()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You have used today's voice allowance. Please type your question, or try again tomorrow.");
        }

        long started = System.nanoTime();
        String text;
        try {
            text = e.transcribe(audio, mimeType, language);
        } catch (SpeechToText.SpeechException ex) {
            log.warn("Speech engine {} failed: {}", e.id(), ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage());
        }
        long millis = (System.nanoTime() - started) / 1_000_000;
        BigDecimal cost = e.costInrPerMinute().multiply(BigDecimal.valueOf(billed)).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
        ledger.record(new AuditEvent(UUID.randomUUID(), Instant.now(), KIND, "speech", subject.tenantId(), subject.userId(),
                text.isBlank() ? "EMPTY" : "OK", null, Set.of(), null, e.id(), e.model(), 0, 0, cost, millis, billed));
        if (text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "I could not hear anything. Please try again, a little closer to the phone.");
        }
        return new Result(text, e.id());
    }

    /** Seconds of audio this person has sent today (kept in the audit row's input_chars column). */
    private int secondsToday(String userId) {
        Instant dayStart = ZonedDateTime.now(IST).truncatedTo(ChronoUnit.DAYS).toInstant();
        Integer n = jdbc.queryForObject(
                "SELECT COALESCE(SUM(input_chars), 0) FROM ai_gateway_events WHERE kind = ? AND user_id = ? AND created_at >= ?",
                Integer.class, KIND, userId, Timestamp.from(dayStart));
        return n == null ? 0 : n;
    }
}
