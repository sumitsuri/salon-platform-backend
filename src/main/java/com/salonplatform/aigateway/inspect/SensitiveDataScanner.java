package com.salonplatform.aigateway.inspect;

import com.salonplatform.aigateway.model.Category;
import com.salonplatform.aigateway.model.Finding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds credentials and personal data in text: API keys and tokens, passwords given in a sentence, connection strings,
 * card numbers, Aadhaar, PAN, bank accounts, phone numbers and emails.
 *
 * <p>Detection is rule-based and deterministic, so the same text always gives the same answer and nothing leaves the
 * process. Findings carry positions and rule ids only, never the matched text.
 */
public final class SensitiveDataScanner {

    private record Rule(String id, Category category, Pattern pattern, int group, Predicate<String> validator) {}

    private static final List<Rule> RULES = List.of(
            rule("private-key", Category.CREDENTIAL, "-----BEGIN [A-Z ]*PRIVATE KEY-----"),
            rule("aws-access-key", Category.CREDENTIAL, "\\b(?:AKIA|ASIA|AGPA|AIDA|AROA)[0-9A-Z]{16}\\b"),
            rule("anthropic-key", Category.CREDENTIAL, "\\bsk-ant-[A-Za-z0-9_\\-]{12,}"),
            rule("openai-key", Category.CREDENTIAL, "\\bsk-(?:proj-|live-|svcacct-)?[A-Za-z0-9_\\-]{20,}"),
            rule("google-api-key", Category.CREDENTIAL, "\\bAIza[0-9A-Za-z_\\-]{35}\\b"),
            rule("github-token", Category.CREDENTIAL, "\\bgh[pousr]_[A-Za-z0-9]{30,}\\b"),
            rule("slack-token", Category.CREDENTIAL, "\\bxox[abprs]-[A-Za-z0-9-]{10,}"),
            rule("stripe-key", Category.CREDENTIAL, "\\b[sr]k_(?:live|test)_[A-Za-z0-9]{16,}\\b"),
            rule("jwt", Category.CREDENTIAL, "\\beyJ[A-Za-z0-9_\\-]{8,}\\.eyJ[A-Za-z0-9_\\-]{8,}\\.[A-Za-z0-9_\\-]{8,}\\b"),
            rule("bearer-token", Category.CREDENTIAL, "(?i)\\bbearer\\s+[A-Za-z0-9._\\-]{20,}"),
            rule("connection-string", Category.CREDENTIAL,
                    "(?i)\\b(?:postgres(?:ql)?|mysql|mongodb(?:\\+srv)?|redis|amqps?)://[^\\s:@/]+:[^\\s@/]+@\\S+"),
            new Rule("password-assignment", Category.CREDENTIAL,
                    Pattern.compile("(?i)\\b(?:password|passwd|pwd|passcode|secret|api[ _-]?key|access[ _-]?token|auth[ _-]?token|"
                            + "private[ _-]?key|client[ _-]?secret|otp|pin)\\b\\s*(?:is|=|:|are|hai|hain|he)\\s*[\"']?([^\\s\"',;]{4,})"),
                    1, Validators::looksLikeSecretValue),
            // Hindi word order: "mera password Sunrise@2026 hai"
            new Rule("password-assignment-hindi-order", Category.CREDENTIAL,
                    Pattern.compile("(?i)\\b(?:password|passwd|pwd|passcode|secret|api[ _-]?key|access[ _-]?token|auth[ _-]?token|"
                            + "private[ _-]?key|client[ _-]?secret|otp|pin)\\b\\s+[\"']?([^\\s\"',;]{4,})[\"']?\\s+(?:hai|hain|he)\\b"),
                    1, Validators::looksLikeSecretValue),
            new Rule("card-number", Category.PAYMENT_CARD, Pattern.compile("(?<![\\d])(?:\\d[ -]?){13,19}(?![\\d])"), 0,
                    v -> Validators.luhn(v.replaceAll("[ -]", ""))),
            rule("card-cvv", Category.PAYMENT_CARD, "(?i)\\bcvv2?\\b\\s*(?:is|=|:)?\\s*\\d{3,4}\\b"),
            new Rule("aadhaar", Category.GOVERNMENT_ID, Pattern.compile("(?<![\\d])[2-9]\\d{3}[ -]?\\d{4}[ -]?\\d{4}(?![\\d])"), 0,
                    v -> Validators.verhoeff(v.replaceAll("[ -]", ""))),
            rule("pan", Category.GOVERNMENT_ID, "\\b[A-Z]{5}\\d{4}[A-Z]\\b"),
            new Rule("bank-account", Category.BANK_ACCOUNT,
                    Pattern.compile("(?i)\\b(?:a/?c|acct|account)(?:\\s*(?:no|number|num|#))?\\.?\\s*(?:is|=|:)?\\s*(\\d{9,18})\\b"),
                    1, v -> true),
            rule("phone", Category.PHONE, "(?<![\\d-])(?:\\+?91[\\s-]?)?[6-9]\\d{9}(?!\\d)"),
            rule("email", Category.EMAIL, "\\b[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}\\b"));

    private static Rule rule(String id, Category category, String regex) {
        return new Rule(id, category, Pattern.compile(regex), 0, v -> true);
    }

    /** Identifiers such as branch ids contain long digit runs that can look like phone numbers; they are never personal data. */
    private static final Pattern UUID = Pattern.compile(
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");

    /** All findings in {@code text}, in order, with overlaps resolved in favour of the earlier, more specific rule. */
    public List<Finding> scan(String text) {
        if (text == null || text.isEmpty()) return List.of();
        List<Finding> found = new ArrayList<>();
        for (Rule r : RULES) {
            Matcher m = r.pattern().matcher(text);
            while (m.find()) {
                String value = r.group() == 0 ? m.group() : m.group(r.group());
                if (value == null || !r.validator().test(value)) continue;
                int start = r.group() == 0 ? m.start() : m.start(r.group());
                int end = r.group() == 0 ? m.end() : m.end(r.group());
                found.add(new Finding(r.category(), r.id(), start, end));
            }
        }
        dropInsideUuids(text, found);
        found.sort(Comparator.comparingInt(Finding::start).thenComparingInt(f -> -(f.end() - f.start())));
        List<Finding> resolved = new ArrayList<>();
        int lastEnd = -1;
        for (Finding f : found) {
            if (f.start() >= lastEnd) {
                resolved.add(f);
                lastEnd = f.end();
            }
        }
        return resolved;
    }

    private static void dropInsideUuids(String text, List<Finding> found) {
        if (found.isEmpty()) return;
        List<int[]> ranges = new ArrayList<>();
        Matcher m = UUID.matcher(text);
        while (m.find()) ranges.add(new int[] {m.start(), m.end()});
        if (ranges.isEmpty()) return;
        found.removeIf(f -> f.category() != Category.CREDENTIAL
                && ranges.stream().anyMatch(r -> f.start() >= r[0] && f.end() <= r[1]));
    }

    /** Replaces the findings in the given categories with a placeholder such as {@code [REDACTED:PHONE]}. */
    public String redact(String text, List<Finding> findings, Set<Category> categories) {
        if (text == null || findings.isEmpty()) return text;
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        for (Finding f : findings) {
            if (!categories.contains(f.category()) || f.start() < cursor) continue;
            out.append(text, cursor, f.start()).append("[REDACTED:").append(f.category().name()).append(']');
            cursor = f.end();
        }
        return out.append(text.substring(cursor)).toString();
    }
}
