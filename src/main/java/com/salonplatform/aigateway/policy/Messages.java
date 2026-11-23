package com.salonplatform.aigateway.policy;

import java.util.Map;

/**
 * Everything the gateway says to a person itself (as opposed to what a model says), in one language. A profile supplies a
 * set per language; the gateway picks by the language the person wrote in and falls back to the profile's default.
 *
 * <p>{@code cooldown} may use {@code {minutes}} and {@code {s}} (an "s" when plural); {@code userLimit} may use {@code {limit}}.
 */
public record Messages(
        Map<SmallTalk, String> smallTalk,
        String offTopic,
        String credential,
        String injection,
        String tooLong,
        String emptyQuestion,
        String cooldown,
        String userLimit,
        String tenantLimit,
        String spendDaily,
        String spendMonthly) {

    public static final String CREDENTIAL = "Your message looks like it contains a password, access key or card number, "
            + "so I did not send it to the AI. Please remove it and ask again. Never share credentials in a chat.";
    public static final String INJECTION = "I can't follow instructions that try to change how I work. I'm here to help "
            + "with your business data, so ask me about your sales, staff, stock or busy hours.";
    public static final String TOO_LONG = "That message is too long for a business question. Please ask it in a sentence or two.";

    /** English texts for the gateway's own messages, around a profile's small talk and off-topic answer. */
    public static Messages english(Map<SmallTalk, String> smallTalk, String offTopic) {
        return new Messages(smallTalk, offTopic, CREDENTIAL, INJECTION, TOO_LONG, "Ask a question first.",
                "You have asked several things I can't help with, so Ask Antra is paused for you for {minutes} more minute{s}. "
                        + "Please ask about your business data afterwards.",
                "You have used today's {limit} questions. Your allowance resets tomorrow.",
                "Today's assistant limit for your business has been reached. It resets tomorrow.",
                "Your business has reached today's assistant usage limit. It resets tomorrow.",
                "Your business has reached this month's assistant usage limit. It resets next month.");
    }
}
