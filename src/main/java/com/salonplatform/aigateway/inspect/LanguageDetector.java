package com.salonplatform.aigateway.inspect;

import com.salonplatform.aigateway.model.Language;

import java.util.Set;

/**
 * Tells English from Hinglish (Hindi in Latin letters, or in Devanagari) without a model, so refusals and fixed answers can
 * be given in the language the person used. It looks for common Hindi function words, which English never uses; the list
 * leaves out words that are also English ("me", "to", "do", "hi", "the").
 */
public final class LanguageDetector {

    /** Short Hindi words that are not English words, in the spellings people actually type. */
    static final Set<String> MARKERS = Set.of(
            "hai", "hain", "tha", "thi", "thay", "hota", "hoti", "hote", "hua", "hui", "hue", "hoga", "hogi", "honge", "raha", "rahi", "rahe",
            "kya", "kyaa", "kyun", "kyu", "kaun", "kon", "kaise", "kaisa", "kaisi", "kab", "kahan", "kidhar", "kis", "kisne", "kitna", "kitni", "kitne",
            "ka", "ki", "ke", "ko", "se", "mein", "par", "pe", "aur", "ya", "nahi", "nahin",
            "batao", "bataiye", "bataye", "bata", "btao", "dikhao", "dikhaiye", "dikha", "dijiye", "karo", "kijiye", "kar", "karna",
            "chahiye", "sakta", "sakte", "sakti", "chalo", "dekho", "dekhna", "samjhao", "sunao",
            "pichle", "pechle", "pichhle", "pichla", "pichhla", "agle", "agla", "mahine", "mahina", "mahinay", "hafte", "hafta", "saal", "aaj", "kal", "abhi",
            "sabse", "zyada", "jyada", "ziyada", "kam", "sirf", "bahut", "bohot", "thoda", "sab", "saare", "sare", "koi", "kuch",
            "mera", "meri", "mere", "hamara", "hamari", "hamare", "humara", "humare", "apna", "apni", "apne", "aap", "aapka", "aapki", "aapke",
            "tum", "tumhara", "wala", "wali", "wale", "waala", "liye", "bhi", "toh", "lekin", "magar", "kyonki", "isliye", "wahan", "yahan",
            "ek", "likho", "likh", "likhiye", "banao", "bana", "mujhe", "mujhko", "humein", "yeh", "woh", "isme", "usme", "iska", "iski", "uska", "uski",
            "kavita", "shayari", "kahani", "chutkula", "chutkule", "mausam", "khabar", "namaste", "namaskar", "shukriya", "dhanyavad", "dhanyawad", "alvida", "theek", "thik", "accha", "acha", "achha", "haan", "ji");

    /** Words so clearly Hindi that one is enough even in a very short message. */
    static final Set<String> CERTAIN = Set.of(
            "hai", "hain", "kya", "kitna", "kitni", "kitne", "batao", "bataiye", "dikhao", "dikhaiye", "pichle", "pechle", "pichhle",
            "mahine", "mahina", "mera", "meri", "mere", "hamare", "aapka", "aapki", "aapke", "namaste", "namaskar", "shukriya", "dhanyavad",
            "sabse", "zyada", "jyada", "kaun", "kaise", "kyun", "likho", "likhiye", "banao", "mujhe", "kavita", "shayari", "kahani", "chutkula", "mausam", "tha", "thi", "nahi", "chahiye", "karo", "kijiye", "haan", "theek", "acha", "accha");

    private LanguageDetector() {}

    public static Language detect(String text) {
        if (text == null || text.isBlank()) return Language.ENGLISH;
        int devanagari = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'ऀ' && c <= 'ॿ') devanagari++;
        }
        if (devanagari >= 2) return Language.HINGLISH;

        String[] tokens = text.toLowerCase(java.util.Locale.ROOT).split("[^\\p{L}]+");
        int words = 0;
        java.util.Set<String> hits = new java.util.HashSet<>();
        boolean certain = false;
        for (String t : tokens) {
            if (t.isEmpty()) continue;
            words++;
            if (MARKERS.contains(t)) {
                hits.add(t);
                if (CERTAIN.contains(t)) certain = true;
            }
        }
        if (hits.isEmpty()) return Language.ENGLISH;
        if (hits.size() >= 2) return Language.HINGLISH;
        // One marker: enough if it is unmistakable, or if the message is only a few words.
        return certain || words <= 3 ? Language.HINGLISH : Language.ENGLISH;
    }
}
