package com.salonplatform.aigateway.inspect;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Spots attempts to override a model's instructions or extract its hidden prompt. Each signal has a weight; a message
 * is flagged when the weights add up to 1.0 or more, so one strong phrase or several weak ones are enough.
 */
public final class PromptInjectionDetector {

    private record Signal(String id, Pattern pattern, double weight) {}

    private static Signal s(String id, String regex, double weight) {
        return new Signal(id, Pattern.compile(regex, Pattern.CASE_INSENSITIVE), weight);
    }

    private static final List<Signal> SIGNALS = List.of(
            s("ignore-instructions", "\\b(ignore|disregard|forget|override|bypass|skip)\\b[^.?!\\n]{0,30}\\b(previous|prior|above|earlier|preceding|all|any|your|the|these)\\b[^.?!\\n]{0,30}\\b(instructions?|rules?|prompts?|guidelines?|restrictions?|guardrails?|filters?|safety|policies|policy|training)\\b", 1.0),
            s("reveal-system-prompt", "\\b(reveal|show|print|repeat|display|leak|output|tell me|give me|share|recite)\\b[^.?!\\n]{0,25}\\b(your|the)\\b[^.?!\\n]{0,15}\\b(system|initial|hidden|original|secret)\\b[^.?!\\n]{0,10}\\b(prompt|instructions?|message|rules)\\b", 1.0),
            s("ask-system-prompt", "\\bwhat (are|is|were) your (system prompt|instructions|rules|guidelines)\\b", 0.8),
            s("jailbreak-mode", "\\b(developer mode|god mode|jailbreak|jailbroken|do anything now|dan mode|unrestricted mode|no restrictions)\\b", 1.0),
            s("role-override", "\\b(you are now|from now on you|you will now act|new instructions:|your new role)\\b", 0.7),
            s("pretend", "\\b(pretend (to be|you are|you're)|roleplay as|role-play as|act as if you (have|had) no)\\b", 0.6),
            s("chat-template-tokens", "(<\\|\\s*(im_start|im_end|system|assistant)\\s*\\|>|\\[/?INST\\]|<<SYS>>|###\\s*(system|instruction)s?\\b|<system>)", 1.0),
            s("encoded-instructions", "\\b(base64|rot13|hex)\\b[^.?!\\n]{0,20}\\b(decode|decoded|instruction|prompt)\\b", 0.6),
            // Hindi in Latin letters: "pichle instructions bhool jao", "system prompt dikhao"
            s("ignore-instructions-hinglish", "\\b(instructions?|rules?|niyam|prompt|guidelines?)\\b[^.?!\\n]{0,30}\\b(bhool|bhul|ignore|todo|tod do|hata|hatao|mat maano|mat mano)\\b"
                    + "|\\b(bhool|bhul|ignore|hatao|hata do)\\b[^.?!\\n]{0,30}\\b(instructions?|rules?|niyam|prompt|guidelines?)\\b", 1.0),
            s("reveal-system-prompt-hinglish", "\\b(system|hidden|secret|original|apna|apni)\\b[^.?!\\n]{0,12}\\b(prompt|instructions?)\\b[^.?!\\n]{0,20}\\b(batao|bataiye|dikhao|dikhaiye|bata do|dikha do|likho|print|reveal|show)\\b", 1.0),
            s("exfiltrate", "\\b(send|post|upload|email)\\b[^.?!\\n]{0,40}\\b(to|at)\\b[^.?!\\n]{0,10}https?://", 0.7));

    /** Total weight of the signals found in {@code normalizedText}. */
    public double score(String normalizedText) {
        double total = 0;
        for (Signal sig : SIGNALS) {
            if (sig.pattern().matcher(normalizedText).find()) total += sig.weight();
        }
        return total;
    }

    private static final Pattern TEMPLATE_TOKENS = Pattern.compile(
            "(<\\|\\s*(im_start|im_end|system|assistant)\\s*\\|>|\\[/?INST\\]|<<SYS>>|###\\s*(system|instruction)s?\\b|<system>)",
            Pattern.CASE_INSENSITIVE);

    /** Chat-template control tokens; a stored "assistant" turn that contains them is an attempt to forge a role. */
    public boolean hasTemplateTokens(String text) {
        return TEMPLATE_TOKENS.matcher(text).find();
    }

    public boolean isInjection(String normalizedText) {
        return score(normalizedText) >= 1.0;
    }
}
