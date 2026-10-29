package com.salonplatform.offerposter;

import com.salonplatform.dto.offerposter.OfferBlockDto;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Writes the WhatsApp message that goes with a poster (WhatsApp *bold*, _italic_ and ~strike~ markup). */
public final class OfferCaptionBuilder {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private OfferCaptionBuilder() {}

    public static String build(OfferContent c) {
        StringBuilder sb = new StringBuilder();
        String emoji = c.occasion().emoji();
        sb.append(emoji).append(" *").append(c.headline().trim()).append("* ").append(emoji).append('\n');
        if (notBlank(c.tagline())) {
            sb.append('_').append(c.tagline().trim()).append("_\n");
        }
        sb.append('\n');

        for (OfferBlockDto b : c.blocks()) {
            appendBlock(sb, b);
        }

        if (c.validUntil() != null) {
            sb.append("⏳ Valid till *").append(c.validUntil().format(DATE)).append("*\n");
        }
        if (notBlank(c.address())) {
            sb.append("📍 ").append(c.brandName().trim()).append(", ").append(c.address().trim()).append('\n');
        } else {
            sb.append("📍 ").append(c.brandName().trim()).append('\n');
        }
        if (notBlank(c.phone())) {
            sb.append("📞 Call / WhatsApp to book: *").append(c.phone().trim()).append("*\n");
        }
        sb.append('\n');
        sb.append("_").append(notBlank(c.terms()) ? c.terms().trim() : "T&C apply • Cannot be clubbed with other offers")
                .append("_");
        return sb.toString();
    }

    private static void appendBlock(StringBuilder sb, OfferBlockDto b) {
        String applies = notBlank(b.getAppliesTo()) ? b.getAppliesTo().trim() : OfferCopy.DEFAULT_APPLIES_TO;
        switch (b.getType()) {
            case SPEND_PERCENT -> {
                for (OfferBlockDto.Tier t : OfferCopy.sortedTiers(b)) {
                    sb.append("✨ Spend *").append(IndianMoney.rupees(t.getMinSpend())).append("+* on ").append(applies)
                            .append(" → *").append(IndianMoney.percent(t.getPercentOff())).append("% OFF*\n");
                }
                sb.append('\n');
            }
            case FREE_WITH_SPEND -> {
                for (OfferBlockDto.Tier t : OfferCopy.sortedTiers(b)) {
                    sb.append("🎁 Spend *").append(IndianMoney.rupees(t.getMinSpend())).append("+* on ").append(applies)
                            .append(" → get *FREE ").append(t.getFreeItem().trim()).append("*\n");
                }
                sb.append('\n');
            }
            case PACKAGE -> {
                OfferCopy.PackagePrice p = OfferCopy.packagePrice(b);
                sb.append("💎 *").append(notBlank(b.getTitle()) ? b.getTitle().trim() : "Festive package").append("*\n");
                List<OfferBlockDto.PackageItem> items = b.getItems();
                for (OfferBlockDto.PackageItem i : items) {
                    sb.append("   • ").append(i.getName().trim()).append('\n');
                }
                sb.append("   ~").append(IndianMoney.rupees(p.mrp())).append("~ → *")
                        .append(IndianMoney.rupees(p.price())).append("* (")
                        .append(IndianMoney.percent(b.getDiscountPercent())).append("% OFF)\n\n");
            }
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
