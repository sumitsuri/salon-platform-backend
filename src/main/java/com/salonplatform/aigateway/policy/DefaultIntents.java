package com.salonplatform.aigateway.policy;

import java.util.List;

/**
 * A library of general-assistant requests that a business-scoped assistant should refuse: the things people ask
 * ChatGPT or Claude for that have nothing to do with the product. Profiles can use all of it or pick from it.
 */
public final class DefaultIntents {

    private DefaultIntents() {}

    public static final IntentRule CODE = IntentRule.of("code",
            "\\b(write|generate|create|build|make|give me|show me|debug|fix|refactor|optimi[sz]e|convert|review)\\b[^.?!\\n]{0,50}"
                    + "\\b(code|script|program|function|algorithm|regex|regular expression|snippet|python|javascript|typescript|java|c\\+\\+|"
                    + "c#|golang|rust|php|ruby|bash|shell script|html|css|react|node\\.?js|sql query|database query|leetcode)\\b"
                    + "|```|\\bdef \\w+\\(|\\bfunction \\w+\\(|#include\\b|public static void main");

    public static final IntentRule CREATIVE = IntentRule.of("creative-writing",
            "\\b(poem|poetry|poems|story|stories|essay|song|lyrics|joke|jokes|riddle|haiku|limerick|shayari|kavita|screenplay|novel|fairy tale)\\b"
                    + "|\\bwrite (me )?(a |an |the |some )?(letter|speech|article|blog|caption|tweet|cover letter|resume|cv|email|message|post|whatsapp|script)\\b");

    public static final IntentRule TRANSLATION = IntentRule.of("translation", "\\btranslate\\b|\\btranslation of\\b");

    public static final IntentRule GENERAL_KNOWLEDGE = IntentRule.of("general-knowledge",
            "\\b(capital of|prime minister|chief minister|president of|who (invented|discovered|wrote|founded|won the)|"
                    + "who is the (president|prime minister|ceo of)|when (was|did|is) [a-z ]{2,30} (born|die|built|founded|invented)|"
                    + "meaning of life|history of|how many (countries|states|planets|continents)|population of|distance (from|between)|fun fact)\\b");

    public static final IntentRule NEWS_MARKETS_WEATHER = IntentRule.of("news-markets-weather",
            "\\b(weather|temperature in|cricket|ipl|world cup|match score|football score|latest news|headlines|election|politics|political|"
                    + "modi|trump|bitcoin|crypto|ethereum|share price|stock price|nifty|sensex|gold rate|petrol price|dollar rate|exchange rate|horoscope|zodiac|lottery)\\b");

    public static final IntentRule HOMEWORK_MATH = IntentRule.of("homework-or-math",
            "\\b(homework|assignment|exam|quiz|syllabus|integral|derivative|theorem|pythagoras|quadratic|solve for x|calculus|physics|chemistry|biology)\\b"
                    + "|^\\s*(what('s| is)|calculate|compute)?\\s*[\\d\\s.,+\\-*/x×÷^()%]{3,}\\s*=?\\s*\\??$");

    public static final IntentRule ENTERTAINMENT_LIFE = IntentRule.of("entertainment-or-personal",
            "\\b(play a game|tic.?tac.?toe|trivia|movie|movies|web series|netflix|song recommendation|recipe|recipes|how to cook|cooking|"
                    + "travel itinerary|trip plan|vacation|dating|girlfriend|boyfriend|relationship advice|wedding speech|"
                    + "tell me about yourself|meaning of life|do you have feelings|i am (bored|lonely|sad))\\b");

    public static final IntentRule HEALTH_LEGAL = IntentRule.of("health-or-legal-advice",
            "\\b(symptoms of|diagnos(e|is)|medicine for|prescription|diet plan|workout plan|lawsuit|legal advice|immigration)\\b");

    public static final IntentRule SCIENCE_TECH = IntentRule.of("science-or-technology",
            "\\b(quantum|relativity|blockchain|machine learning|artificial intelligence|climate change|photosynthesis|black hole|"
                    + "solar system|neural network|world war|theory of|chatgpt|openai|llm)\\b");

    /** Every rule above, for profiles that want the whole library. */
    public static List<IntentRule> all() {
        return List.of(CODE, CREATIVE, TRANSLATION, GENERAL_KNOWLEDGE, NEWS_MARKETS_WEATHER, HOMEWORK_MATH, ENTERTAINMENT_LIFE,
                HEALTH_LEGAL, SCIENCE_TECH);
    }
}
