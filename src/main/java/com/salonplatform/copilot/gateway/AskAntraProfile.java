package com.salonplatform.copilot.gateway;

import com.salonplatform.aigateway.model.Action;
import com.salonplatform.aigateway.model.Language;
import com.salonplatform.aigateway.policy.Messages;
import com.salonplatform.aigateway.policy.Budget;
import com.salonplatform.aigateway.policy.DefaultIntents;
import com.salonplatform.aigateway.policy.Profile;
import com.salonplatform.aigateway.policy.SmallTalk;
import com.salonplatform.config.CopilotProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * What "on topic" means for Ask Antra: running a salon or spa business. The words below are the vocabulary of that
 * world; a question that uses none of them, nor a branch, staff or service name of the asking brand, is not treated as
 * a business question unless a classifier says otherwise.
 */
@Configuration
public class AskAntraProfile {

    public static final String NAME = "ask-antra";

    /**
     * Words that on their own show a question is about running this business. Chosen to be hard to mistake for general
     * chat: "sales" and "branch" are, "book" and "profit" are not (they also mean other things), so those sit below.
     */
    static final Set<String> DOMAIN_ROOTS = Set.of(
            "sale", "revenue", "salon", "spa", "branch", "outlet", "staff", "stylist", "therap", "customer", "booking",
            "appoint", "attendance", "payroll", "gst", "upi", "antra", "performer", "parlour", "parlor",
            // Hindi in Latin letters, in the spellings people type (bikri, bikree; munafa, munaafa; ...). Words that also have
            // everyday meanings (kamai, kharch, nuksan, vetan) are weak below, so "paise kaise kamaye" does not pass on its own.
            "bikr", "munaf", "munaaf", "naaf", "nafa", "grahak", "graahak", "karmchari", "karamchari", "karmachari", "tankh",
            "dukan", "dukaa", "shakha",
            // Devanagari: bikri, sale(s), munafa, grahak, karmchari, staff, dukan, shakha, booking
            "बिक्री", "सेल", "मुनाफ", "नफा", "ग्राहक", "कर्मचारी", "स्टाफ", "दुकान", "शाखा", "बुकिंग", "सैलरी", "तनख्वाह", "ब्रांच", "कस्टमर");

    /**
     * Words that fit this business but also fit everyday talk. Two different ones together, or one that a small model
     * confirms, make a question on topic; one alone never refuses a question but does not clear it either.
     */
    static final Set<String> WEAK_ROOTS = Set.of(
            "profit", "loss", "income", "expense", "margin", "discount", "offer", "coupon", "packag", "member", "invoice", "payment",
            "cash", "tax", "ticket", "averag", "rupee", "budget", "refund", "client", "guest", "walk", "visit", "retent", "repeat",
            "returning", "loyal", "campaign", "promo", "whatsapp", "employ", "attend", "salar", "incentiv", "commission", "target",
            "roster", "busy", "peak", "quiet", "idle", "stretch", "weekday", "weekend", "festiv", "season", "servic", "treat", "hair",
            "facial", "massag", "colour", "color", "keratin", "bridal", "makeup", "nail", "wax", "pedicur", "manicur", "inventor",
            "stock", "product", "reorder", "vendor", "supplier", "consum", "restock", "forecast", "trend", "compar", "declin",
            "growth", "increas", "decreas", "report", "dashboard", "kpi", "insight", "business", "brand", "performanc", "review",
            // Hindi in Latin letters: income, spending, loss, money, accounts
            "kamai", "kamay", "kharch", "nuksa", "aamdan", "amdan", "vetan", "hisab", "hisaab", "bachat",
            // Devanagari: income, expense, loss, money
            "कमाई", "खर्च", "नुकसान", "आमदनी", "वेतन", "हिसाब", "मुनाफा");

    @Bean
    public Profile askAntraGatewayProfile(CopilotProperties props) {
        Map<SmallTalk, String> smallTalk = new EnumMap<>(SmallTalk.class);
        smallTalk.put(SmallTalk.GREETING, "Hello! I'm Antra. Ask me about your sales, staff, busy hours, stock or profit, for example "
                + "\"How were sales in the last 30 days?\"");
        smallTalk.put(SmallTalk.THANKS, "You're welcome! Ask me anything else about your business.");
        smallTalk.put(SmallTalk.GOODBYE, "Goodbye! I'll be here whenever you want to look at your numbers.");
        smallTalk.put(SmallTalk.IDENTITY, "I'm Antra, the assistant built into AntraHQ for your salon or spa. I read your own business "
                + "data and suggest what to do. I can't chat about other topics.");
        smallTalk.put(SmallTalk.CAPABILITIES, "I can look up and explain sales, visits and average ticket by branch, why sales changed, "
                + "staff performance against salary targets, attendance, busy and quiet hours, stock, and profit and loss. "
                + "Try \"Who are my top performers this month?\"");

        Budget budget = new Budget(props.getDailyRequestsPerUser(), props.getDailyRequestsPerTenant(), props.getDailySpendInrPerTenant(),
                props.getMonthlySpendInrPerTenant(), props.getViolationsBeforeCooldown(), props.getCooldownMinutes());

        String offTopic = "I can only help with your business: your sales, staff, busy hours, stock and profit. I can't help with general "
                + "questions, writing or coding. Try asking, for example, \"Which branch is doing best this month?\"";
        return new Profile(NAME, true, DOMAIN_ROOTS, WEAK_ROOTS, DefaultIntents.all(), smallTalk, offTopic,
                150, Profile.defaultSensitiveActions(), budget, Action.BLOCK, Action.ALLOW, Map.of(Language.HINGLISH, hinglish()));
    }

    /**
     * Owners write to Antra in Hindi using Latin letters, so a refusal or a fixed answer must come back the same way, or
     * English becomes the barrier. Business terms (sales, branch, GST) stay in English because that is how owners say them.
     */
    static Messages hinglish() {
        Map<SmallTalk, String> talk = new EnumMap<>(SmallTalk.class);
        talk.put(SmallTalk.GREETING, "Namaste! Main Antra hoon. Mujhse apni sales, staff, busy hours, stock ya profit ke baare mein "
                + "poochhiye, jaise \"Pichle 30 din mein sales kitni thi?\"");
        talk.put(SmallTalk.THANKS, "Aapka swagat hai! Apne business ke baare mein aur kuch poochhna ho to bataiye.");
        talk.put(SmallTalk.GOODBYE, "Alvida! Jab bhi apne numbers dekhne hon, main yahin hoon.");
        talk.put(SmallTalk.IDENTITY, "Main Antra hoon, AntraHQ mein aapke salon ya spa ke liye bana assistant. Main aapke business ka data "
                + "padhkar batata hoon ki kya karna chahiye. Dusre topics par main baat nahi kar sakta.");
        talk.put(SmallTalk.CAPABILITIES, "Main branch ke hisaab se sales, visits aur average ticket, sales kyun badhi ya ghati, staff ka "
                + "performance salary target ke saamne, attendance, busy aur quiet hours, stock, aur profit-loss bata sakta hoon. "
                + "Poochhiye, jaise \"Is mahine sabse achhe performer kaun hain?\"");
        return new Messages(talk,
                "Main sirf aapke business mein madad kar sakta hoon: sales, staff, busy hours, stock aur profit. General sawal, likhna "
                        + "ya coding mere bas ki baat nahi. Aise poochhiye, jaise \"Is mahine kaun sa branch sabse achha chal raha hai?\"",
                "Aapke message mein password, access key ya card number lag raha hai, isliye maine use AI ko nahi bheja. Kripya use hata "
                        + "kar dobara poochhiye. Chat mein kabhi password ya card number share na karein.",
                "Main aise instructions follow nahi kar sakta jo mere kaam karne ka tareeka badalne ki koshish karte hain. Main aapke "
                        + "business data mein madad ke liye hoon, to sales, staff, stock ya busy hours ke baare mein poochhiye.",
                "Yeh message business ke sawal ke liye bahut lamba hai. Kripya ek-do line mein poochhiye.",
                "Pehle ek sawal poochhiye.",
                "Aapne kai aise sawal puchhe jin mein main madad nahi kar sakta, isliye Ask Antra aapke liye {minutes} minute ke liye "
                        + "roka gaya hai. Uske baad apne business data ke baare mein poochhiye.",
                "Aaj ke {limit} sawal aap use kar chuke hain. Kal aapka limit dobara shuru ho jayega.",
                "Aaj ke liye aapke business ka assistant limit poora ho gaya hai. Kal se dobara chalega.",
                "Aapke business ne aaj ka assistant usage limit poora kar liya hai. Kal se dobara chalega.",
                "Aapke business ne is mahine ka assistant usage limit poora kar liya hai. Agle mahine se dobara chalega.");
    }
}
