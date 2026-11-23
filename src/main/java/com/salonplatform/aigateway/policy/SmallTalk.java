package com.salonplatform.aigateway.policy;

import java.util.regex.Pattern;

/**
 * Messages that need no model: a greeting, thanks, "who are you". Answering them from a fixed text costs nothing and
 * removes a common way to burn budget on chit-chat.
 */
public enum SmallTalk {
    GREETING("^(hi+|hello+|hey+|hola|namaste|namaskar|good (morning|afternoon|evening)|yo)( (antra|there|team))?$"),
    THANKS("^(thanks|thank you|thx|ty|thanks a lot|thank you so much|many thanks|shukriya|dhanyavad|great|ok|okay|cool|got it|nice|awesome|perfect|fine|alright)( antra)?$"),
    GOODBYE("^(bye|goodbye|see you|see ya|good night|cya|take care)( antra)?$"),
    IDENTITY("^(who are you|what are you|are you (an )?(ai|bot|robot|chatgpt|gpt|claude|gemini)|which (ai |llm )?model( are you)?( using)?|what model are you|what is your name|what's your name|your name)$"),
    CAPABILITIES("^(help|what can you do|what do you do|how can you help( me)?|what can i ask( you)?|how do i use (this|you)|what are your capabilities|what can you help (me )?with)$");

    private final Pattern pattern;

    SmallTalk(String regex) {
        this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    public boolean matches(String normalizedText) {
        String t = normalizedText.replaceAll("[\\p{Punct}\\s]+$", "").replaceAll("^[\\p{Punct}\\s]+", "").trim();
        return pattern.matcher(t).matches();
    }
}
