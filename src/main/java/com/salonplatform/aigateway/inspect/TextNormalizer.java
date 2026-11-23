package com.salonplatform.aigateway.inspect;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Makes text comparable: removes invisible characters people use to slip past filters, and folds look-alikes. */
public final class TextNormalizer {

    private static final Pattern INVISIBLE = Pattern.compile("[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\uFEFF\\u00AD]");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\x0B\\f\\r]+");

    private TextNormalizer() {}

    /** Unicode-folded and de-obfuscated text, for pattern matching. Offsets are not preserved; use it for detection. */
    public static String forMatching(String text) {
        if (text == null) return "";
        String t = Normalizer.normalize(text, Normalizer.Form.NFKC);
        t = INVISIBLE.matcher(t).replaceAll("");
        t = CONTROL.matcher(t).replaceAll(" ");
        return SPACES.matcher(t).replaceAll(" ").trim();
    }

    public static String lower(String text) {
        return forMatching(text).toLowerCase(Locale.ROOT);
    }
}
