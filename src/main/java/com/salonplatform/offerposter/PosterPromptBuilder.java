package com.salonplatform.offerposter;

import com.salonplatform.dto.offerposter.OfferBlockDto;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Turns an offer into the prompt for the image model. The wording matters: image models routinely misspell or invent
 * text, so the prompt lists every string that may appear, in quotes, and forbids everything else.
 */
public final class PosterPromptBuilder {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private PosterPromptBuilder() {}

    public static String build(OfferContent c) {
        boolean portrait = c.format() == PosterFormat.PORTRAIT;
        StringBuilder p = new StringBuilder();

        p.append("Design a premium, eye-catching promotional poster for an Indian salon, to be shared on WhatsApp and ")
                .append("Instagram. Format: ").append(portrait ? "vertical portrait (2:3)" : "square (1:1)").append(".\n\n");

        p.append("OCCASION: ").append(c.occasion().label()).append(". Scene and mood: ").append(c.occasion().scene()).append(".\n");
        p.append("VISUAL STYLE: ").append(c.style().direction()).append(".\n");
        p.append("HERO IMAGE: a beautiful, elegant Indian woman model with flawless glowing skin in festive attire, ")
                .append("positioned so she does not cover any text. Photorealistic, high-end beauty-campaign quality.\n");
        if (notBlank(c.extraDirection())) {
            p.append("EXTRA DIRECTION FROM THE OWNER: ").append(c.extraDirection().trim()).append(".\n");
        }

        p.append("\nTEXT TO RENDER — write each string EXACTLY as given between the quotation marks, spelled correctly, ")
                .append("with large clear legible lettering and good contrast against its background. Use the Indian rupee ")
                .append("sign ₹. Do not alter, translate, abbreviate, round or reorder any number:\n");

        p.append("1. Brand name at the top: \"").append(c.brandName().trim().toUpperCase(Locale.ROOT)).append("\"\n");
        p.append("2. Main headline, the largest text: \"").append(c.headline().trim()).append("\"\n");
        int n = 3;
        if (notBlank(c.tagline())) {
            p.append(n++).append(". Sub-headline: \"").append(c.tagline().trim()).append("\"\n");
        }

        p.append(n++).append(". Offer cards (each as its own clearly separated panel, in this order):\n");
        int card = 1;
        for (OfferCopy.Rendered r : OfferCopy.render(c.blocks())) {
            appendCard(p, card++, r, c.blocks());
        }

        if (c.includedServices() != null && !c.includedServices().isEmpty()) {
            p.append(n++).append(". A side panel titled \"Salon Services Include\" listing exactly: ");
            for (int i = 0; i < c.includedServices().size(); i++) {
                if (i > 0) p.append("; ");
                p.append('"').append(c.includedServices().get(i).trim()).append('"');
            }
            p.append(". Add a small simple icon next to each.\n");
        }

        if (c.validUntil() != null) {
            p.append(n++).append(". Validity line: \"Valid till ").append(c.validUntil().format(DATE)).append("\"\n");
        }

        StringBuilder footer = new StringBuilder();
        footer.append(c.brandName().trim());
        if (notBlank(c.address())) footer.append(" — ").append(c.address().trim());
        p.append(n++).append(". Location banner near the bottom: \"").append(footer).append("\"\n");
        if (notBlank(c.phone())) {
            p.append(n++).append(". Booking line with a WhatsApp/phone icon: \"Call / WhatsApp to Book\" and the number \"")
                    .append(c.phone().trim()).append("\" (write this number exactly once, in one place)\n");
        }
        p.append(n).append(". Tiny footer line: \"")
                .append(notBlank(c.terms()) ? c.terms().trim() : "T&C apply • Cannot be clubbed with other offers")
                .append("\"\n");

        p.append("\nSTRICT RULES: Show NO text, numbers, prices, percentages, phone numbers, addresses, dates, logos, ")
                .append("watermarks or slogans other than the strings listed above. Never invent or change an offer, price, ")
                .append("discount or phone number. Keep the layout uncluttered with generous spacing so every word is ")
                .append("sharp and readable on a phone screen, and keep all text well inside the edges of the image. ")
                .append("Decorative handwritten flourishes must contain no words.");
        return p.toString();
    }

    private static void appendCard(StringBuilder p, int index, OfferCopy.Rendered r, List<OfferBlockDto> blocks) {
        p.append("   Card ").append(index).append(" (").append(r.heading()).append("):\n");
        switch (r.type()) {
            case SPEND_PERCENT -> {
                p.append("     Show one rounded tier panel per line, each with the spend amount big and the discount prominent:\n");
                for (String line : r.lines()) p.append("     - \"").append(line.replace(" → ", " — ")).append("\"\n");
            }
            case FREE_WITH_SPEND -> {
                p.append("     Show one rounded tier panel per line, each with a small illustration of the free service and a ")
                        .append("bold 'FREE' highlight:\n");
                for (String line : r.lines()) p.append("     - \"").append(line.replace(" → ", " — ")).append("\"\n");
            }
            case PACKAGE -> {
                p.append("     Package panel titled \"").append(r.heading()).append("\" listing its services with their prices, ")
                        .append("then the old total with a strike-through and the new package price in a large gold badge, ")
                        .append("with a discount seal:\n");
                for (String line : r.lines()) p.append("     - \"").append(line.replace(" → ", " — ")).append("\"\n");
            }
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
