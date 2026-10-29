package com.salonplatform.offerposter;

import com.salonplatform.dto.offerposter.OfferBlockDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The single source of truth for what each offer says. The caption, the poster prompt and the on-screen summary all
 * read from here so a price can never differ between them.
 */
public final class OfferCopy {

    public static final String DEFAULT_APPLIES_TO = "any salon services";

    private OfferCopy() {}

    /** A block's wording: a heading and the lines under it. */
    public record Rendered(OfferBlockType type, String heading, List<String> lines) {}

    public static List<Rendered> render(List<OfferBlockDto> blocks) {
        List<Rendered> out = new ArrayList<>();
        if (blocks == null) return out;
        for (OfferBlockDto b : blocks) {
            out.add(render(b));
        }
        return out;
    }

    public static Rendered render(OfferBlockDto b) {
        String appliesTo = blank(b.getAppliesTo()) ? DEFAULT_APPLIES_TO : b.getAppliesTo().trim();
        List<String> lines = new ArrayList<>();
        switch (b.getType()) {
            case SPEND_PERCENT -> {
                for (OfferBlockDto.Tier t : sortedTiers(b)) {
                    lines.add("Spend " + IndianMoney.rupees(t.getMinSpend()) + "+ on " + appliesTo
                            + " → " + IndianMoney.percent(t.getPercentOff()) + "% OFF");
                }
                return new Rendered(b.getType(), "Spend more, save more", lines);
            }
            case FREE_WITH_SPEND -> {
                for (OfferBlockDto.Tier t : sortedTiers(b)) {
                    lines.add("Spend " + IndianMoney.rupees(t.getMinSpend()) + "+ on " + appliesTo
                            + " → Get FREE " + t.getFreeItem().trim());
                }
                return new Rendered(b.getType(), "Free with your bill", lines);
            }
            case PACKAGE -> {
                PackagePrice p = packagePrice(b);
                String heading = blank(b.getTitle()) ? "Festive package" : b.getTitle().trim();
                for (OfferBlockDto.PackageItem i : b.getItems()) {
                    lines.add(i.getName().trim() + " " + IndianMoney.rupees(i.getPrice()));
                }
                lines.add("Total " + IndianMoney.rupees(p.mrp()) + " → " + IndianMoney.percent(b.getDiscountPercent())
                        + "% OFF → Package price " + IndianMoney.rupees(p.price()));
                return new Rendered(b.getType(), heading, lines);
            }
            default -> throw new IllegalStateException("Unknown offer type " + b.getType());
        }
    }

    public record PackagePrice(BigDecimal mrp, BigDecimal price) {}

    /** Combined list price and the price after the package discount, to whole rupees. */
    public static PackagePrice packagePrice(OfferBlockDto b) {
        BigDecimal mrp = BigDecimal.ZERO;
        for (OfferBlockDto.PackageItem i : b.getItems()) {
            mrp = mrp.add(i.getPrice() == null ? BigDecimal.ZERO : i.getPrice());
        }
        BigDecimal pct = b.getDiscountPercent() == null ? BigDecimal.ZERO : b.getDiscountPercent();
        BigDecimal price = mrp.multiply(BigDecimal.valueOf(100).subtract(pct))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        return new PackagePrice(mrp.setScale(0, RoundingMode.HALF_UP), price);
    }

    static List<OfferBlockDto.Tier> sortedTiers(OfferBlockDto b) {
        List<OfferBlockDto.Tier> tiers = new ArrayList<>(b.getTiers());
        tiers.sort((a, c) -> a.getMinSpend().compareTo(c.getMinSpend()));
        return tiers;
    }

    /** One short line per block, for the summary shown next to the form. */
    public static List<String> summary(List<OfferBlockDto> blocks) {
        List<String> out = new ArrayList<>();
        for (Rendered r : render(blocks)) {
            if (r.type() == OfferBlockType.PACKAGE) {
                out.add(r.heading() + ": " + r.lines().get(r.lines().size() - 1));
            } else {
                out.addAll(r.lines());
            }
        }
        return out;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
