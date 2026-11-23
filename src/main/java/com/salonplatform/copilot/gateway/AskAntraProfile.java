package com.salonplatform.copilot.gateway;

import com.salonplatform.aigateway.model.Action;
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
            "appoint", "attendance", "payroll", "gst", "upi", "antra", "performer",
            // a little Hindi and Hinglish
            "bikri", "kamai", "munafa", "kharch", "grahak", "karmchari", "tankhwah");

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
            "growth", "increas", "decreas", "report", "dashboard", "kpi", "insight", "business", "brand", "performanc", "review");

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

        return new Profile(NAME, true, DOMAIN_ROOTS, WEAK_ROOTS, DefaultIntents.all(), smallTalk,
                "I can only help with your business: your sales, staff, busy hours, stock and profit. I can't help with general "
                        + "questions, writing or coding. Try asking, for example, \"Which branch is doing best this month?\"",
                150, Profile.defaultSensitiveActions(), budget, Action.BLOCK, Action.ALLOW);
    }
}
