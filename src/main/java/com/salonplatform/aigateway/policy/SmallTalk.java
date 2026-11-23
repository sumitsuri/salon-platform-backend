package com.salonplatform.aigateway.policy;

import java.util.regex.Pattern;

/**
 * Messages that need no model: a greeting, thanks, "who are you". Answering them from a fixed text costs nothing and
 * removes a common way to burn budget on chit-chat.
 */
public enum SmallTalk {
    GREETING("^(hi+|hello+|hey+|hola|namaste|namaskar|good (morning|afternoon|evening)|yo|namaste( ji)?|namaskar|pranam|ram ram|salaam|salam|(aap |tum )?kaise (ho|hain)|kya haal( hai| chaal)?|haal chaal|\\u0928\\u092e\\u0938\\u094d\\u0924\\u0947|\\u0928\\u092e\\u0938\\u094d\\u0915\\u093e\\u0930)( (antra|there|team|ji))?$"),
    THANKS("^(thanks|thank you|thx|ty|thanks a lot|thank you so much|many thanks|shukriya|dhanyavad|great|ok|okay|cool|got it|nice|awesome|perfect|fine|alright|dhanyawad|bahut (bahut )?(shukriya|dhanyavad|dhanyawad)|(theek|thik) hai|acha|accha|achha|badhiya|shandar|ok ji|ji|haan ji|\\u0927\\u0928\\u094d\\u092f\\u0935\\u093e\\u0926|\\u0936\\u0941\\u0915\\u094d\\u0930\\u093f\\u092f\\u093e)( (antra|ji))?$"),
    GOODBYE("^(bye|goodbye|see you|see ya|good night|cya|take care|alvida|phir milte hain|chalta hoon|chalti hoon|shubh ratri)( (antra|ji))?$"),
    IDENTITY("^(who are you|what are you|are you (an )?(ai|bot|robot|chatgpt|gpt|claude|gemini)|which (ai |llm )?model( are you)?( using)?|what model are you|what is your name|what's your name|your name|(tum|aap) kaun (ho|hain)|(tum|aap) kya (ho|hain)|(tumhara|aapka|tera) naam kya hai|(tumhara|aapka) naam|kya (tum|aap) (ai|bot|chatgpt|robot) (ho|hain))$"),
    CAPABILITIES("^(help|what can you do|what do you do|how can you help( me)?|what can i ask( you)?|how do i use (this|you)|what are your capabilities|what can you help (me )?with|madad|madad karo|help karo|(aap|tum) kya (kya )?kar sakte (ho|hain)|kya kya kar sakte (ho|hain)|main kya (puch|pooch) sakta (hoon|hu)|kaise use karu|kya puch sakta hoon)$");

    private final Pattern pattern;

    SmallTalk(String regex) {
        this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    public boolean matches(String normalizedText) {
        String t = normalizedText.replaceAll("[\\p{Punct}\\s]+$", "").replaceAll("^[\\p{Punct}\\s]+", "").trim();
        return pattern.matcher(t).matches();
    }
}
