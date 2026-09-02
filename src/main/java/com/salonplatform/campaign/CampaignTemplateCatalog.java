package com.salonplatform.campaign;

import com.salonplatform.domain.enums.BookingSource;
import com.salonplatform.domain.enums.CampaignMembershipFilter;
import com.salonplatform.domain.enums.CampaignTemplateCategoryCode;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class CampaignTemplateCatalog {

    private static final List<CampaignTemplateDefinition> TEMPLATES = List.of(
            // A. Win-back & retention (1–6)
            template("winback-30d-lapsed", CampaignTemplateCategoryCode.WINBACK,
                    "30-day lapsed win-back",
                    "Customers who visited before but haven't returned in the last 30 days.",
                    "Retention",
                    "We miss you! Enjoy 15% off your next visit this week — book your slot today.",
                    CampaignTemplateFilterPreset.builder()
                            .minVisitCount(1)
                            .lastVisitToDaysAgo(30)
                            .lastVisitFromDaysAgo(90)
                            .build()),
            template("winback-90d-vip", CampaignTemplateCategoryCode.WINBACK,
                    "90-day VIP win-back",
                    "High spenders (₹2,000+) who haven't visited in 90+ days.",
                    "Retention",
                    "It's been a while — complimentary head massage with any service when you book this week.",
                    CampaignTemplateFilterPreset.builder()
                            .minLifetimeSpend(new BigDecimal("2000"))
                            .lastVisitToDaysAgo(90)
                            .minVisitCount(1)
                            .build()),
            template("winback-one-and-done", CampaignTemplateCategoryCode.WINBACK,
                    "One-and-done first-timers",
                    "Visited exactly once and haven't returned in 21+ days.",
                    "Retention",
                    "How was your first visit? Book again and get ₹200 off your next service.",
                    CampaignTemplateFilterPreset.builder()
                            .minVisitCount(1)
                            .maxVisitCount(1)
                            .lastVisitToDaysAgo(21)
                            .build()),
            template("winback-high-value-lapsed", CampaignTemplateCategoryCode.WINBACK,
                    "High-value lapsed VIP",
                    "Lifetime spend above ₹15,000 with no visit in 45+ days.",
                    "Retention",
                    "Your preferred stylist saved a priority slot for you this weekend — book before it fills up.",
                    CampaignTemplateFilterPreset.builder()
                            .minLifetimeSpend(new BigDecimal("15000"))
                            .lastVisitToDaysAgo(45)
                            .minVisitCount(1)
                            .build()),
            template("winback-60d-mid", CampaignTemplateCategoryCode.WINBACK,
                    "60-day mid-tier win-back",
                    "Regular customers (2+ visits) absent for 60+ days.",
                    "Retention",
                    "Your glow routine is due — 20% off any service when you rebook this month.",
                    CampaignTemplateFilterPreset.builder()
                            .minVisitCount(2)
                            .lastVisitToDaysAgo(60)
                            .build()),
            template("winback-recent-7d-nudge", CampaignTemplateCategoryCode.WINBACK,
                    "Recent visitor rebook nudge",
                    "Visited in the last 7 days — nudge for next appointment before they lapse.",
                    "Retention",
                    "Loved your last visit? Pre-book your next slot now and get 10% off.",
                    CampaignTemplateFilterPreset.builder()
                            .minVisitCount(1)
                            .lastVisitFromDaysAgo(7)
                            .build()),

            // B. Membership growth (7–12)
            template("membership-frequent-non-member", CampaignTemplateCategoryCode.MEMBERSHIP,
                    "Frequent visitor — no membership",
                    "4+ visits and no active membership — pitch savings.",
                    "Upsell",
                    "You've been visiting often — our membership saves 20% every time. Ask us at your next visit!",
                    CampaignTemplateFilterPreset.builder()
                            .minVisitCount(4)
                            .membershipFilter(CampaignMembershipFilter.NON_MEMBER)
                            .build()),
            template("membership-savings-pitch", CampaignTemplateCategoryCode.MEMBERSHIP,
                    "Membership savings calculator",
                    "Lifetime spend above ₹8,000 without membership.",
                    "Upsell",
                    "Join Gold membership — 20% off every visit. Pays for itself in just 2 visits!",
                    CampaignTemplateFilterPreset.builder()
                            .minLifetimeSpend(new BigDecimal("8000"))
                            .membershipFilter(CampaignMembershipFilter.NON_MEMBER)
                            .build()),
            template("membership-expiring-soon", CampaignTemplateCategoryCode.MEMBERSHIP,
                    "Membership expiring soon",
                    "Active members whose plan expires within 14 days.",
                    "Retention",
                    "Your membership expires soon — renew now to keep your benefits and get 1 free add-on.",
                    CampaignTemplateFilterPreset.builder()
                            .membershipFilter(CampaignMembershipFilter.EXPIRING_SOON)
                            .membershipExpiringWithinDays(14)
                            .build()),
            template("membership-expired-winback", CampaignTemplateCategoryCode.MEMBERSHIP,
                    "Expired member win-back",
                    "Membership expired in the last 90 days.",
                    "Retention",
                    "Welcome back — renew at your old rate if you book this week!",
                    CampaignTemplateFilterPreset.builder()
                            .membershipFilter(CampaignMembershipFilter.EXPIRED)
                            .build()),
            template("membership-walk-in-repeater", CampaignTemplateCategoryCode.MEMBERSHIP,
                    "Walk-in repeaters → membership",
                    "3+ walk-in visits without membership.",
                    "Upsell",
                    "Stop paying full price — ask about our monthly membership with unlimited blow-dry benefits.",
                    CampaignTemplateFilterPreset.builder()
                            .minVisitCount(3)
                            .membershipFilter(CampaignMembershipFilter.NON_MEMBER)
                            .bookingSource(BookingSource.WALK_IN)
                            .build()),
            template("membership-new-regular", CampaignTemplateCategoryCode.MEMBERSHIP,
                    "New regular — membership intro",
                    "2–5 visits, no membership, visited in last 30 days.",
                    "Upsell",
                    "You're becoming a regular! Unlock member pricing from your next visit — ask our front desk.",
                    CampaignTemplateFilterPreset.builder()
                            .minVisitCount(2)
                            .maxVisitCount(5)
                            .membershipFilter(CampaignMembershipFilter.NON_MEMBER)
                            .lastVisitFromDaysAgo(30)
                            .build()),

            // C. Premium service upsell (13–18)
            template("premium-non-trier", CampaignTemplateCategoryCode.PREMIUM_UPSELL,
                    "Premium service non-trier",
                    "Regular visitors who never tried keratin, botox, or straightening.",
                    "Upsell",
                    "Smooth frizz for 6 months — book a keratin demo slot this Saturday!",
                    CampaignTemplateFilterPreset.builder()
                            .minVisitCount(2)
                            .excludeServiceKeywords(List.of("keratin", "botox", "straightening", "smoothening", "rebonding"))
                            .build()),
            template("premium-colour-to-keratin", CampaignTemplateCategoryCode.PREMIUM_UPSELL,
                    "Colour client → keratin cross-sell",
                    "Had hair colour recently, never had keratin/smoothening.",
                    "Cross-sell",
                    "Protect your fresh colour — keratin + colour combo at 25% off this week only.",
                    CampaignTemplateFilterPreset.builder()
                            .serviceKeywords(List.of("colour", "color", "highlight", "balayage"))
                            .excludeServiceKeywords(List.of("keratin", "smoothening", "straightening"))
                            .lastVisitFromDaysAgo(90)
                            .build()),
            template("premium-facial-upgrade", CampaignTemplateCategoryCode.PREMIUM_UPSELL,
                    "Basic facial → advanced facial",
                    "Had basic cleanup/facial, not hydra or anti-ageing.",
                    "Upsell",
                    "Upgrade to HydraFacial — first session at basic facial price. Limited slots!",
                    CampaignTemplateFilterPreset.builder()
                            .serviceKeywords(List.of("cleanup", "basic facial", "fruit facial"))
                            .excludeServiceKeywords(List.of("hydra", "anti-age", "anti age", "advanced facial"))
                            .build()),
            template("premium-mens-beard-spa", CampaignTemplateCategoryCode.PREMIUM_UPSELL,
                    "Men's grooming → beard spa",
                    "Men's haircut customers who haven't tried beard spa/grooming.",
                    "Cross-sell",
                    "Add beard spa at ₹299 — free with any haircut this week!",
                    CampaignTemplateFilterPreset.builder()
                            .serviceKeywords(List.of("haircut", "hair cut", "trim"))
                            .excludeServiceKeywords(List.of("beard spa", "beard", "grooming"))
                            .serviceCategoryKeywords(List.of("men", "gents"))
                            .build()),
            template("premium-bridal-upstyle", CampaignTemplateCategoryCode.PREMIUM_UPSELL,
                    "Bridal / upstyle opportunity",
                    "Regular colour clients who never booked bridal/upstyle.",
                    "Upsell",
                    "Wedding season is here — trial updo + makeup at 30% off. Book your consultation!",
                    CampaignTemplateFilterPreset.builder()
                            .serviceKeywords(List.of("colour", "color", "highlight"))
                            .excludeServiceKeywords(List.of("bridal", "updo", "upstyle", "party makeup"))
                            .minVisitCount(2)
                            .build()),
            template("premium-botox-consult", CampaignTemplateCategoryCode.PREMIUM_UPSELL,
                    "Botox / aesthetic consult nudge",
                    "Facial/skincare clients who haven't tried injectables.",
                    "Upsell",
                    "Free 10-min consult with our aesthetic specialist — Thursday slots open. Reply to book!",
                    CampaignTemplateFilterPreset.builder()
                            .serviceCategoryKeywords(List.of("skin", "facial", "spa"))
                            .excludeServiceKeywords(List.of("botox", "filler", "injectable"))
                            .minVisitCount(1)
                            .build()),

            // D. Cross-sell bundles (19–23)
            template("cross-hair-to-skin", CampaignTemplateCategoryCode.CROSS_SELL,
                    "Hair → skin cross-sell",
                    "Hair-only customers who never had facial/spa.",
                    "Cross-sell",
                    "Glow to match your hair — express facial at ₹499 for our hair clients only!",
                    CampaignTemplateFilterPreset.builder()
                            .serviceCategoryKeywords(List.of("hair"))
                            .excludeServiceCategoryKeywords(List.of("skin", "facial", "spa"))
                            .minVisitCount(1)
                            .build()),
            template("cross-facial-to-hair", CampaignTemplateCategoryCode.CROSS_SELL,
                    "Facial → hair cross-sell",
                    "Spa/facial-only customers with no hair service history.",
                    "Cross-sell",
                    "Complete the look — trim + blow-dry add-on at ₹399 with your next facial.",
                    CampaignTemplateFilterPreset.builder()
                            .serviceCategoryKeywords(List.of("skin", "facial", "spa"))
                            .excludeServiceCategoryKeywords(List.of("hair"))
                            .minVisitCount(1)
                            .build()),
            template("cross-mani-pedi", CampaignTemplateCategoryCode.CROSS_SELL,
                    "Mani-pedi attach rate",
                    "Hair service customers without mani/pedi in 6 months.",
                    "Cross-sell",
                    "Mani + pedi combo at ₹799 when you book any hair service this week!",
                    CampaignTemplateFilterPreset.builder()
                            .serviceCategoryKeywords(List.of("hair"))
                            .excludeServiceKeywords(List.of("mani", "pedi", "manicure", "pedicure", "nail"))
                            .lastVisitFromDaysAgo(180)
                            .build()),
            template("cross-head-spa-addon", CampaignTemplateCategoryCode.CROSS_SELL,
                    "Head spa add-on upsell",
                    "High ticket visitors (₹1,500+) who may not have tried head spa.",
                    "Upsell",
                    "Your last visit qualifies for a complimentary 15-min head spa — book your next slot today!",
                    CampaignTemplateFilterPreset.builder()
                            .minLifetimeSpend(new BigDecimal("1500"))
                            .excludeServiceKeywords(List.of("head spa", "head massage", "scalp"))
                            .lastVisitFromDaysAgo(60)
                            .build()),
            template("cross-waxing-bundle", CampaignTemplateCategoryCode.CROSS_SELL,
                    "Threading → waxing bundle",
                    "Threading clients who haven't tried waxing.",
                    "Cross-sell",
                    "Try our waxing combo — 20% off when bundled with your regular threading visit.",
                    CampaignTemplateFilterPreset.builder()
                            .serviceKeywords(List.of("threading", "brow"))
                            .excludeServiceKeywords(List.of("wax", "waxing"))
                            .build()),

            // E. Reviews & reputation (24–27)
            template("review-detractor-recovery", CampaignTemplateCategoryCode.REVIEWS,
                    "Detractor recovery",
                    "Customers who rated 3 stars or below.",
                    "Retention",
                    "We're sorry your last visit wasn't perfect — our manager will call you. Complimentary service on us.",
                    CampaignTemplateFilterPreset.builder()
                            .maxOverallRating(3)
                            .hasSubmittedReview(true)
                            .build()),
            template("review-detractor-lapsed", CampaignTemplateCategoryCode.REVIEWS,
                    "Low rating & not returning",
                    "Rated ≤3 and haven't visited in 30+ days.",
                    "Retention",
                    "We want to make it right — book a complimentary touch-up and share your feedback with us.",
                    CampaignTemplateFilterPreset.builder()
                            .maxOverallRating(3)
                            .hasSubmittedReview(true)
                            .lastVisitToDaysAgo(30)
                            .build()),
            template("review-promoter-google", CampaignTemplateCategoryCode.REVIEWS,
                    "Promoter → Google review",
                    "Rated 4+ stars but haven't been redirected to Google review.",
                    "Advocacy",
                    "You loved us! A 30-second Google review = ₹100 off your next visit. Thank you!",
                    CampaignTemplateFilterPreset.builder()
                            .minOverallRating(4)
                            .googleReviewNotSubmitted(true)
                            .build()),
            template("review-no-feedback", CampaignTemplateCategoryCode.REVIEWS,
                    "No review after recent visit",
                    "Visited in last 14 days but no review submitted.",
                    "Advocacy",
                    "How did we do? Rate your last visit and unlock 10% off your next booking!",
                    CampaignTemplateFilterPreset.builder()
                            .hasSubmittedReview(false)
                            .minVisitCount(1)
                            .lastVisitFromDaysAgo(14)
                            .build()),

            // F. VIP & behavioural (28–30)
            template("vip-whale-appreciation", CampaignTemplateCategoryCode.VIP_BEHAVIOURAL,
                    "Whale appreciation",
                    "Lifetime spend above ₹25,000, active in last 60 days.",
                    "Retention",
                    "VIP early access — new premium line launch. Invite-only preview this weekend!",
                    CampaignTemplateFilterPreset.builder()
                            .minLifetimeSpend(new BigDecimal("25000"))
                            .lastVisitFromDaysAgo(60)
                            .build()),
            template("vip-low-ticket-frequent", CampaignTemplateCategoryCode.VIP_BEHAVIOURAL,
                    "Low ticket, high frequency",
                    "6+ visits with moderate spend (under ₹5,000 lifetime).",
                    "Upsell",
                    "Try our signature premium service — upgrade any visit at ₹500 off this month!",
                    CampaignTemplateFilterPreset.builder()
                            .minVisitCount(6)
                            .maxLifetimeSpend(new BigDecimal("5000"))
                            .build()),
            template("vip-weekday-fill", CampaignTemplateCategoryCode.VIP_BEHAVIOURAL,
                    "Weekday rebook nudge",
                    "Active customers (visited in 60 days) — fill quieter weekday slots.",
                    "Fill capacity",
                    "Quiet weekday special — 20% off Tue–Thu when you book your next visit!",
                    CampaignTemplateFilterPreset.builder()
                            .minVisitCount(2)
                            .lastVisitFromDaysAgo(60)
                            .build())
    );

    private CampaignTemplateCatalog() {}

    public static List<CampaignTemplateDefinition> all() {
        return TEMPLATES;
    }

    public static Optional<CampaignTemplateDefinition> findById(String id) {
        return TEMPLATES.stream().filter(t -> t.getId().equals(id)).findFirst();
    }

    public static List<CampaignTemplateDefinition> byCategory(CampaignTemplateCategoryCode category) {
        return TEMPLATES.stream().filter(t -> t.getCategory() == category).toList();
    }

    private static CampaignTemplateDefinition template(
            String id,
            CampaignTemplateCategoryCode category,
            String name,
            String description,
            String goal,
            String suggestedMessage,
            CampaignTemplateFilterPreset filterPreset) {
        return CampaignTemplateDefinition.builder()
                .id(id)
                .category(category)
                .name(name)
                .description(description)
                .goal(goal)
                .suggestedMessage(suggestedMessage)
                .filterPreset(filterPreset)
                .build();
    }

    /** Category display order for the template library UI. */
    public static List<CampaignTemplateCategoryCode> categoryOrder() {
        return Arrays.asList(CampaignTemplateCategoryCode.values());
    }
}
