package com.salonplatform.aigateway.policy;

import com.salonplatform.aigateway.model.Action;
import com.salonplatform.aigateway.model.Category;
import com.salonplatform.aigateway.model.Language;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The rules for one product surface. An application defines a profile per chat it offers; the gateway applies it.
 *
 * @param name                 profile id, referenced by requests
 * @param scopeGuard           refuse questions that are not about the profile's domain
 * @param domainRoots          strong word roots: one of them puts a message on topic (\"sale\" matches \"sales\", \"overstaffed\" matches \"staff\")
 * @param weakDomainRoots      generic roots that only count together (two different ones) or with a classifier's agreement
 * @param deniedIntents        general-assistant requests to refuse outright
 * @param smallTalk            fixed answers for greetings and similar, keyed by kind; kinds not listed go to the model
 * @param offTopicMessage      what to say when a question is out of scope
 * @param maxWords             longer messages are refused (essays and pasted documents are not business questions)
 * @param sensitiveActions     what to do with each kind of sensitive data found in a question
 * @param budget               spend and rate limits
 * @param ambiguousWithoutClassifier what to do with a question that has no domain words and no classifier is available
 * @param weakSignalWithoutClassifier what to do with a question that has exactly one weak domain word and no classifier is available
 * @param localized            the gateway's own messages per language; a language not listed gets the English texts built from {@code smallTalk} and {@code offTopicMessage}
 */
public record Profile(
        String name,
        boolean scopeGuard,
        Set<String> domainRoots,
        Set<String> weakDomainRoots,
        List<IntentRule> deniedIntents,
        Map<SmallTalk, String> smallTalk,
        String offTopicMessage,
        int maxWords,
        Map<Category, Action> sensitiveActions,
        Budget budget,
        Action ambiguousWithoutClassifier,
        Action weakSignalWithoutClassifier,
        Map<Language, Messages> localized) {

    /** A profile with English messages only. */
    public Profile(String name, boolean scopeGuard, Set<String> domainRoots, Set<String> weakDomainRoots, List<IntentRule> deniedIntents,
                   Map<SmallTalk, String> smallTalk, String offTopicMessage, int maxWords, Map<Category, Action> sensitiveActions,
                   Budget budget, Action ambiguousWithoutClassifier, Action weakSignalWithoutClassifier) {
        this(name, scopeGuard, domainRoots, weakDomainRoots, deniedIntents, smallTalk, offTopicMessage, maxWords, sensitiveActions, budget,
                ambiguousWithoutClassifier, weakSignalWithoutClassifier, Map.of());
    }

    /** The gateway's own messages in the given language. */
    public Messages messages(Language language) {
        Messages m = localized == null ? null : localized.get(language);
        return m != null ? m : Messages.english(smallTalk, offTopicMessage);
    }

    /** What to do with sensitive data unless a profile says otherwise: block secrets and cards, redact personal data. */
    public static Map<Category, Action> defaultSensitiveActions() {
        Map<Category, Action> m = new EnumMap<>(Category.class);
        m.put(Category.CREDENTIAL, Action.BLOCK);
        m.put(Category.PAYMENT_CARD, Action.BLOCK);
        m.put(Category.GOVERNMENT_ID, Action.REDACT);
        m.put(Category.BANK_ACCOUNT, Action.REDACT);
        m.put(Category.PHONE, Action.REDACT);
        m.put(Category.EMAIL, Action.REDACT);
        return m;
    }

    /** A profile with no topic restriction and no limits: only sensitive-data and injection protection apply. */
    public static Profile protectionOnly(String name) {
        return new Profile(name, false, Set.of(), Set.of(), List.of(), Map.of(), "", 0, defaultSensitiveActions(),
                Budget.unlimited(), Action.ALLOW, Action.ALLOW);
    }

    public Action actionFor(Category category) {
        return sensitiveActions.getOrDefault(category, Action.ALLOW);
    }
}
