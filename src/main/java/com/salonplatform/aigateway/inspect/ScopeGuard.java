package com.salonplatform.aigateway.inspect;

import com.salonplatform.aigateway.policy.IntentRule;
import com.salonplatform.aigateway.policy.Profile;
import com.salonplatform.aigateway.policy.SmallTalk;

import java.util.Set;

/**
 * Decides whether a question belongs to a profile's domain, using cheap deterministic rules so the clear cases cost
 * nothing: greetings are answered from a fixed text, general-assistant requests are refused, messages with domain
 * words (or the subject's own names) pass. Only genuinely unclear questions are handed to a classifier.
 */
public final class ScopeGuard {

    public enum Result { IN_SCOPE, OFF_TOPIC, AMBIGUOUS, SMALL_TALK, TOO_LONG }

    /** The outcome; {@code detail} is the refused intent id, or the small-talk kind. */
    public record Verdict(Result result, String detail, SmallTalk smallTalk) {}

    /** A follow-up such as "and last month?" in a conversation already on topic is short; longer ones need a reason. */
    static final int FOLLOW_UP_MAX_WORDS = 14;

    public Verdict evaluate(Profile profile, String lowerText, Set<String> contextTerms, boolean priorInScope) {
        for (SmallTalk kind : SmallTalk.values()) {
            if (profile.smallTalk().containsKey(kind) && kind.matches(lowerText)) {
                return new Verdict(Result.SMALL_TALK, kind.name(), kind);
            }
        }
        String[] tokens = tokens(lowerText);
        if (profile.maxWords() > 0 && tokens.length > profile.maxWords()) {
            return new Verdict(Result.TOO_LONG, "max-words", null);
        }
        for (IntentRule rule : profile.deniedIntents()) {
            if (rule.pattern().matcher(lowerText).find()) {
                return new Verdict(Result.OFF_TOPIC, rule.id(), null);
            }
        }
        Signal signal = signal(profile, tokens, contextTerms);
        if (signal.strong() > 0 || signal.weakDistinct() >= 2) {
            return new Verdict(Result.IN_SCOPE, "domain-terms", null);
        }
        if (priorInScope && tokens.length <= FOLLOW_UP_MAX_WORDS) {
            return new Verdict(Result.IN_SCOPE, "follow-up", null);
        }
        return new Verdict(Result.AMBIGUOUS, signal.weakDistinct() == 1 ? WEAK_SIGNAL : NO_SIGNAL, null);
    }

    /** Detail for an ambiguous question with exactly one generic domain word. */
    public static final String WEAK_SIGNAL = "weak-signal";
    /** Detail for an ambiguous question with no domain word at all. */
    public static final String NO_SIGNAL = "no-signal";

    /** How strongly a text links to the domain. */
    public record Signal(int strong, int weakDistinct) {
        public int total() {
            return strong + weakDistinct;
        }
    }

    /** How many on-topic words the text has: strong ones (and the subject's own names), and distinct weak ones. */
    public Signal signal(Profile profile, String lowerText, Set<String> contextTerms) {
        return signal(profile, tokens(lowerText), contextTerms);
    }

    private Signal signal(Profile profile, String[] tokens, Set<String> contextTerms) {
        int strong = 0;
        Set<String> weak = new java.util.HashSet<>();
        for (String token : tokens) {
            if (token.length() < 2) continue;
            if (matches(profile.domainRoots(), token) || matchesTerm(contextTerms, token)) {
                strong++;
            } else {
                String root = weakRoot(profile.weakDomainRoots(), token);
                if (root != null) weak.add(root);
            }
        }
        return new Signal(strong, weak.size());
    }

    /**
     * Short roots (gst, upi, spa) must match whole words; four-letter roots match word starts (sale, sales, salesman);
     * longer roots match anywhere in the word, so "overstaffed" and "understaffing" still count as "staff".
     */
    private static boolean matches(Set<String> roots, String token) {
        if (roots.contains(token)) return true;
        for (String root : roots) {
            if (root.length() >= 5 && token.contains(root)) return true;
            if (root.length() == 4 && token.startsWith(root)) return true;
        }
        return false;
    }

    private static String weakRoot(Set<String> roots, String token) {
        if (roots.contains(token)) return token;
        for (String root : roots) {
            if (root.length() >= 5 && token.contains(root)) return root;
            if (root.length() == 4 && token.startsWith(root)) return root;
        }
        return null;
    }

    private static boolean matchesTerm(Set<String> terms, String token) {
        return token.length() >= 3 && terms.contains(token);
    }

    private static String[] tokens(String lower) {
        return lower.split("[^\\p{L}\\p{N}]+");
    }
}
