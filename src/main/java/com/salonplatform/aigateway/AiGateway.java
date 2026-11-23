package com.salonplatform.aigateway;

import com.salonplatform.aigateway.inspect.PromptInjectionDetector;
import com.salonplatform.aigateway.inspect.ScopeGuard;
import com.salonplatform.aigateway.inspect.SensitiveDataScanner;
import com.salonplatform.aigateway.inspect.TextNormalizer;
import com.salonplatform.aigateway.ledger.AuditEvent;
import com.salonplatform.aigateway.ledger.GatewayLedger;
import com.salonplatform.aigateway.ledger.UsageSnapshot;
import com.salonplatform.aigateway.model.Action;
import com.salonplatform.aigateway.model.Category;
import com.salonplatform.aigateway.model.Finding;
import com.salonplatform.aigateway.model.GatewayContext;
import com.salonplatform.aigateway.model.GatewayRequest;
import com.salonplatform.aigateway.model.InboundDecision;
import com.salonplatform.aigateway.model.Message;
import com.salonplatform.aigateway.model.ModelUsage;
import com.salonplatform.aigateway.model.OutboundDecision;
import com.salonplatform.aigateway.model.Subject;
import com.salonplatform.aigateway.policy.Budget;
import com.salonplatform.aigateway.policy.CostCalculator;
import com.salonplatform.aigateway.policy.Profile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The gateway between people and a language model. Every chat goes through it twice: on the way in (is this allowed,
 * on topic, free of secrets, within budget) and on the way out (does the answer leak anything). It knows nothing about
 * the application that calls it; behaviour comes from a {@link Profile} and two small interfaces, {@link GatewayLedger}
 * and {@link ScopeClassifier}.
 *
 * <p>Order on the way in, cheapest first so refusals cost nothing: cool-down, prompt injection, secrets and personal
 * data, message length, topic, budgets, and only then (for unclear questions) a classifier.
 */
public final class AiGateway {

    public static final String MSG_CREDENTIAL = "Your message looks like it contains a password, access key or card number, "
            + "so I did not send it to the AI. Please remove it and ask again. Never share credentials in a chat.";
    public static final String MSG_INJECTION = "I can't follow instructions that try to change how I work. I'm here to help "
            + "with your business data, so ask me about your sales, staff, stock or busy hours.";
    public static final String MSG_TOO_LONG = "That message is too long for a business question. Please ask it in a sentence or two.";

    /** What one subject has used and may use, for showing remaining allowance. */
    public record UsageReport(UsageSnapshot used, Budget limits) {}

    private final Map<String, Profile> profiles;
    private final Profile fallbackProfile;
    private final SensitiveDataScanner scanner = new SensitiveDataScanner();
    private final PromptInjectionDetector injection = new PromptInjectionDetector();
    private final ScopeGuard scope = new ScopeGuard();
    private final GatewayLedger ledger;
    private final CostCalculator cost;
    private final ScopeClassifier classifier;
    private final Clock clock;
    private final ZoneId zone;

    public AiGateway(Collection<Profile> profiles, GatewayLedger ledger, CostCalculator cost, ScopeClassifier classifier,
                     Clock clock, ZoneId zone) {
        this.profiles = profiles.stream().collect(Collectors.toMap(Profile::name, p -> p, (a, b) -> b));
        this.fallbackProfile = Profile.protectionOnly("default");
        this.ledger = ledger;
        this.cost = cost;
        this.classifier = classifier;
        this.clock = clock;
        this.zone = zone;
    }

    // ------------------------------------------------------------------ inbound

    public InboundDecision inspectInbound(GatewayRequest req) {
        Instant now = clock.instant();
        Profile profile = profiles.getOrDefault(req.profile(), fallbackProfile);
        GatewayContext ctx = new GatewayContext(UUID.randomUUID(), req.channel(), profile.name(), req.subject(), req.provider(),
                req.model(), now);
        List<Message> normalised = req.messages() == null ? List.of() : req.messages().stream()
                .map(m -> new Message(m.role(), TextNormalizer.forMatching(m.text()))).toList();
        int last = lastUserIndex(normalised);
        if (last < 0 || normalised.get(last).text().isBlank()) {
            return block(ctx, "EMPTY", "Ask a question first.", false, List.of(), 0);
        }
        int inputChars = normalised.get(last).text().length();
        Budget budget = profile.budget();
        Set<String> terms = lowerTerms(req.contextTerms());

        // 1. A cool-down after repeated refused attempts
        UsageSnapshot used = needsSnapshot(budget) ? snapshot(req.subject(), now) : UsageSnapshot.empty();
        if (budget.violationsBeforeCooldown() > 0 && budget.cooldownMinutes() > 0
                && used.violationsLastHour() >= budget.violationsBeforeCooldown() && used.lastViolationAt() != null) {
            Instant until = used.lastViolationAt().plus(Duration.ofMinutes(budget.cooldownMinutes()));
            if (now.isBefore(until)) {
                long minutes = Math.max(1, (Duration.between(now, until).getSeconds() + 59) / 60);
                return limit(ctx, "COOLDOWN", "You have asked several things I can't help with, so Ask Antra is paused for you for "
                        + minutes + " more minute" + (minutes == 1 ? "" : "s") + ". Please ask about your business data afterwards.", inputChars);
            }
        }

        // 2. Prompt injection in anything the person wrote; role-spoofing tokens in "assistant" history
        for (Message m : normalised) {
            String lower = m.text().toLowerCase(Locale.ROOT);
            boolean hit = m.role() == Message.Role.USER ? injection.isInjection(lower) : injection.hasTemplateTokens(m.text());
            if (hit) {
                return block(ctx, Category.PROMPT_INJECTION.name(), MSG_INJECTION, false, List.of(), inputChars,
                        Category.PROMPT_INJECTION, "prompt-injection");
            }
        }

        // 3. Secrets and personal data, in the whole conversation
        List<Message> clean = new ArrayList<>();
        Set<Category> redacted = EnumSet.noneOf(Category.class);
        for (int i = 0; i < normalised.size(); i++) {
            Message m = normalised.get(i);
            List<Finding> findings = scanner.scan(m.text());
            if (m.role() == Message.Role.USER) {
                Finding blocking = findings.stream().filter(f -> profile.actionFor(f.category()) == Action.BLOCK).findFirst().orElse(null);
                if (blocking != null) {
                    return block(ctx, blocking.category().name(), MSG_CREDENTIAL, i == last, List.of(blocking), inputChars,
                            blocking.category(), blocking.ruleId());
                }
            }
            Set<Category> toRedact = EnumSet.noneOf(Category.class);
            for (Finding f : findings) {
                // An answer or old turn that somehow holds a secret is redacted rather than refused.
                if (m.role() == Message.Role.ASSISTANT || profile.actionFor(f.category()) == Action.REDACT) toRedact.add(f.category());
            }
            if (toRedact.isEmpty()) {
                clean.add(m);
            } else {
                redacted.addAll(toRedact);
                clean.add(new Message(m.role(), scanner.redact(m.text(), findings, toRedact)));
            }
        }
        if (!redacted.isEmpty()) {
            audit(AuditEvent.REDACT, ctx, "REDACT", "INPUT", redacted, "", 0, 0, BigDecimal.ZERO, 0, inputChars);
        }

        // 4. Topic
        String lastLower = normalised.get(last).text().toLowerCase(Locale.ROOT);
        ScopeGuard.Verdict verdict = null;
        if (profile.scopeGuard()) {
            boolean priorInScope = false;
            for (int i = 0; i < last && !priorInScope; i++) {
                Message m = normalised.get(i);
                priorInScope = m.role() == Message.Role.USER
                        && scope.evaluate(profile, m.text().toLowerCase(Locale.ROOT), terms, false).result() == ScopeGuard.Result.IN_SCOPE;
            }
            verdict = scope.evaluate(profile, lastLower, terms, priorInScope);
            switch (verdict.result()) {
                case SMALL_TALK -> {
                    String answer = profile.smallTalk().get(verdict.smallTalk());
                    audit(AuditEvent.ANSWERED, ctx, "ANSWERED", verdict.detail(), Set.of(), "", 0, 0, BigDecimal.ZERO, 0, inputChars);
                    return new InboundDecision(InboundDecision.Kind.ANSWERED, verdict.detail(), answer, false, List.of(), List.of(), ctx);
                }
                case TOO_LONG -> {
                    return block(ctx, Category.TOO_LONG.name(), MSG_TOO_LONG, false, List.of(), inputChars, Category.TOO_LONG, "max-words");
                }
                case OFF_TOPIC -> {
                    return block(ctx, Category.OFF_TOPIC.name(), profile.offTopicMessage(), false, List.of(), inputChars,
                            Category.OFF_TOPIC, verdict.detail());
                }
                default -> { /* IN_SCOPE or AMBIGUOUS: continue */ }
            }
        }

        // 5. Budgets
        InboundDecision over = overBudget(ctx, budget, used, inputChars);
        if (over != null) return over;

        // 6. A second opinion for questions the rules cannot place
        if (verdict != null && verdict.result() == ScopeGuard.Result.AMBIGUOUS) {
            Boolean inScope = null;
            if (classifier != null) {
                ScopeClassifier.Result r = classifier.classify(ctx, profile, normalised.get(last).text());
                inScope = r.inScope();
                audit(AuditEvent.CLASSIFY, ctx, inScope == null ? "UNKNOWN" : inScope ? "ALLOW" : "BLOCK", "scope-classifier", Set.of(),
                        "", r.inputTokens(), r.outputTokens(),
                        r.model() == null ? BigDecimal.ZERO : cost.costInr(r.model(), r.inputTokens(), r.outputTokens()), 0, inputChars);
            }
            Action fallback = ScopeGuard.WEAK_SIGNAL.equals(verdict.detail())
                    ? profile.weakSignalWithoutClassifier() : profile.ambiguousWithoutClassifier();
            boolean allow = inScope != null ? inScope : fallback == Action.ALLOW;
            if (!allow) {
                return block(ctx, Category.OFF_TOPIC.name(), profile.offTopicMessage(), false, List.of(), inputChars,
                        Category.OFF_TOPIC, inScope == null ? "unclassified" : "classifier");
            }
        }

        audit(AuditEvent.REQUEST, ctx, "ALLOW", "OK", Set.of(), "", 0, 0, BigDecimal.ZERO, 0, inputChars);
        return new InboundDecision(InboundDecision.Kind.ALLOW, "OK", null, false, clean, List.of(), ctx);
    }

    // ------------------------------------------------------------------ in between and outbound

    /** Redacts credentials and personal data from tool output before a model sees it. */
    public String sanitizeToolResult(GatewayContext ctx, String text) {
        if (text == null || text.isEmpty()) return text;
        List<Finding> findings = scanner.scan(text);
        if (findings.isEmpty()) return text;
        Set<Category> cats = findings.stream().map(Finding::category).collect(Collectors.toCollection(() -> EnumSet.noneOf(Category.class)));
        audit(AuditEvent.REDACT, ctx, "REDACT", "TOOL_RESULT", cats, rules(findings), 0, 0, BigDecimal.ZERO, 0, text.length());
        return scanner.redact(text, findings, cats);
    }

    /**
     * Checks a model's answer: sensitive data is redacted, and an answer that recites the hidden instructions is
     * replaced. {@code protectedFragments} are distinctive phrases from those instructions.
     */
    public OutboundDecision inspectOutbound(GatewayContext ctx, String answer, Collection<String> protectedFragments) {
        if (answer == null || answer.isBlank()) return new OutboundDecision(Action.ALLOW, answer, List.of());
        String lower = answer.toLowerCase(Locale.ROOT);
        long leaked = protectedFragments == null ? 0 : protectedFragments.stream()
                .filter(f -> f != null && !f.isBlank() && lower.contains(f.toLowerCase(Locale.ROOT))).count();
        if (leaked >= 2) {
            audit(AuditEvent.BLOCK, ctx, "BLOCK", "OUTPUT_" + Category.SYSTEM_PROMPT_LEAK.name(), EnumSet.of(Category.SYSTEM_PROMPT_LEAK),
                    "system-prompt-fragments", 0, 0, BigDecimal.ZERO, 0, answer.length());
            return new OutboundDecision(Action.BLOCK, "I can't share that. I can help with your sales, staff, stock and busy hours.",
                    List.of(new Finding(Category.SYSTEM_PROMPT_LEAK, "system-prompt-fragments", 0, answer.length())));
        }
        List<Finding> findings = scanner.scan(answer);
        if (findings.isEmpty()) return new OutboundDecision(Action.ALLOW, answer, List.of());
        Set<Category> cats = findings.stream().map(Finding::category).collect(Collectors.toCollection(() -> EnumSet.noneOf(Category.class)));
        audit(AuditEvent.REDACT, ctx, "REDACT", "OUTPUT", cats, rules(findings), 0, 0, BigDecimal.ZERO, 0, answer.length());
        return new OutboundDecision(Action.REDACT, scanner.redact(answer, findings, cats), findings);
    }

    /** Records what a model call used, so its cost counts against the business's budget. */
    public void recordUsage(GatewayContext ctx, ModelUsage usage) {
        String model = usage.model() != null ? usage.model() : ctx.model();
        audit(AuditEvent.USAGE, ctx, usage.success() ? "OK" : "ERROR", usage.success() ? "OK" : "MODEL_ERROR", Set.of(), "",
                usage.inputTokens(), usage.outputTokens(), cost.costInr(model, usage.inputTokens(), usage.outputTokens()),
                usage.latencyMillis(), 0, model);
    }

    /** The allowance a subject has used and the limits that apply, for showing "12 of 60 questions today". */
    public UsageReport usage(Subject subject, String profileName) {
        Profile profile = profiles.getOrDefault(profileName, fallbackProfile);
        return new UsageReport(snapshot(subject, clock.instant()), profile.budget());
    }

    // ------------------------------------------------------------------ internals

    private InboundDecision overBudget(GatewayContext ctx, Budget b, UsageSnapshot u, int inputChars) {
        if (b.dailyRequestsPerUser() > 0 && u.requestsUserToday() >= b.dailyRequestsPerUser()) {
            return limit(ctx, "RATE_LIMIT_USER", "You have used today's " + b.dailyRequestsPerUser()
                    + " questions. Your allowance resets tomorrow.", inputChars);
        }
        if (b.dailyRequestsPerTenant() > 0 && u.requestsTenantToday() >= b.dailyRequestsPerTenant()) {
            return limit(ctx, "RATE_LIMIT_TENANT", "Today's assistant limit for your business has been reached. It resets tomorrow.", inputChars);
        }
        if (b.dailySpendPerTenantInr() > 0 && u.spendTenantToday().doubleValue() >= b.dailySpendPerTenantInr()) {
            return limit(ctx, "SPEND_DAILY", "Your business has reached today's assistant usage limit. It resets tomorrow.", inputChars);
        }
        if (b.monthlySpendPerTenantInr() > 0 && u.spendTenantMonth().doubleValue() >= b.monthlySpendPerTenantInr()) {
            return limit(ctx, "SPEND_MONTHLY", "Your business has reached this month's assistant usage limit. It resets next month.", inputChars);
        }
        return null;
    }

    private static boolean needsSnapshot(Budget b) {
        return b.dailyRequestsPerUser() > 0 || b.dailyRequestsPerTenant() > 0 || b.dailySpendPerTenantInr() > 0
                || b.monthlySpendPerTenantInr() > 0 || b.violationsBeforeCooldown() > 0;
    }

    private UsageSnapshot snapshot(Subject s, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, zone);
        return ledger.snapshot(s.tenantId(), s.userId(), today.atStartOfDay(zone).toInstant(),
                today.withDayOfMonth(1).atStartOfDay(zone).toInstant(), now.minus(Duration.ofHours(1)));
    }

    private InboundDecision block(GatewayContext ctx, String reason, String message, boolean discard, List<Finding> findings,
                                  int inputChars, Category category, String ruleId) {
        audit(AuditEvent.BLOCK, ctx, "BLOCK", reason, EnumSet.of(category), ruleId, 0, 0, BigDecimal.ZERO, 0, inputChars);
        return new InboundDecision(InboundDecision.Kind.BLOCK, reason, message, discard, List.of(), findings, ctx);
    }

    private InboundDecision block(GatewayContext ctx, String reason, String message, boolean discard, List<Finding> findings, int inputChars) {
        audit(AuditEvent.BLOCK, ctx, "BLOCK", reason, Set.of(), "", 0, 0, BigDecimal.ZERO, 0, inputChars);
        return new InboundDecision(InboundDecision.Kind.BLOCK, reason, message, discard, List.of(), findings, ctx);
    }

    private InboundDecision limit(GatewayContext ctx, String reason, String message, int inputChars) {
        audit(AuditEvent.LIMIT, ctx, "BLOCK", reason, Set.of(), "", 0, 0, BigDecimal.ZERO, 0, inputChars);
        return new InboundDecision(InboundDecision.Kind.LIMIT, reason, message, false, List.of(), List.of(), ctx);
    }

    private void audit(String kind, GatewayContext ctx, String decision, String reason, Set<Category> categories, String ruleIds,
                        long tokensIn, long tokensOut, BigDecimal costInr, long latencyMillis, int inputChars) {
        audit(kind, ctx, decision, reason, categories, ruleIds, tokensIn, tokensOut, costInr, latencyMillis, inputChars, ctx.model());
    }

    private void audit(String kind, GatewayContext ctx, String decision, String reason, Set<Category> categories, String ruleIds,
                        long tokensIn, long tokensOut, BigDecimal costInr, long latencyMillis, int inputChars, String model) {
        try {
            ledger.record(new AuditEvent(UUID.randomUUID(), clock.instant(), kind, ctx.channel(), ctx.subject().tenantId(),
                    ctx.subject().userId(), decision, reason, new LinkedHashSet<>(categories), ruleIds, ctx.provider(), model,
                    tokensIn, tokensOut, costInr, latencyMillis, inputChars));
        } catch (RuntimeException e) {
            // An audit failure must never take chat down, but it must not pass silently either.
            org.slf4j.LoggerFactory.getLogger(AiGateway.class).error("ai-gateway audit write failed: {}", e.toString());
        }
    }

    private static int lastUserIndex(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).role() == Message.Role.USER) return i;
        }
        return -1;
    }

    private static Set<String> lowerTerms(Set<String> terms) {
        if (terms == null) return Set.of();
        return terms.stream().filter(t -> t != null && !t.isBlank()).map(t -> t.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    private static String rules(List<Finding> findings) {
        return findings.stream().map(Finding::ruleId).distinct().collect(Collectors.joining(","));
    }
}
