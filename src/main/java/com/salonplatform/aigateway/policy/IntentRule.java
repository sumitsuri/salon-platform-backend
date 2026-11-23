package com.salonplatform.aigateway.policy;

import java.util.regex.Pattern;

/** A kind of request a profile refuses, such as writing code or poems, recognised by a pattern on the lower-cased text. */
public record IntentRule(String id, Pattern pattern) {

    public static IntentRule of(String id, String regex) {
        return new IntentRule(id, Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL));
    }
}
