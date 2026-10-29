package com.salonplatform.offerposter;

import com.salonplatform.dto.offerposter.OfferBlockDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OfferCopyTest {

    public static OfferBlockDto.Tier tier(String min, String pct, String free) {
        OfferBlockDto.Tier t = new OfferBlockDto.Tier();
        t.setMinSpend(new BigDecimal(min));
        if (pct != null) t.setPercentOff(new BigDecimal(pct));
        t.setFreeItem(free);
        return t;
    }

    public static OfferBlockDto spend(OfferBlockDto.Tier... tiers) {
        OfferBlockDto b = new OfferBlockDto();
        b.setType(OfferBlockType.SPEND_PERCENT);
        b.getTiers().addAll(List.of(tiers));
        return b;
    }

    public static OfferBlockDto free(OfferBlockDto.Tier... tiers) {
        OfferBlockDto b = new OfferBlockDto();
        b.setType(OfferBlockType.FREE_WITH_SPEND);
        b.getTiers().addAll(List.of(tiers));
        return b;
    }

    public static OfferBlockDto pkg(String title, String pct, String... nameAndPrice) {
        OfferBlockDto b = new OfferBlockDto();
        b.setType(OfferBlockType.PACKAGE);
        b.setTitle(title);
        b.setDiscountPercent(new BigDecimal(pct));
        for (int i = 0; i < nameAndPrice.length; i += 2) {
            OfferBlockDto.PackageItem it = new OfferBlockDto.PackageItem();
            it.setName(nameAndPrice[i]);
            it.setPrice(new BigDecimal(nameAndPrice[i + 1]));
            b.getItems().add(it);
        }
        return b;
    }

    public static OfferContent content(OfferBlockDto... blocks) {
        return new OfferContent(PosterOccasion.DIWALI, PosterStyle.FESTIVE_MAROON_GOLD, PosterFormat.PORTRAIT,
                "Mystic Salon", "Karwa Chauth & Diwali Beauty Bonus", "Glow, Glam & Celebrate",
                "Alpine Pyramid Clubhouse", "9731617930", LocalDate.of(2026, 11, 15), null,
                List.of(blocks), List.of("Manicure & Pedicure", "Facials"), null);
    }

    @Test
    void indianGrouping() {
        assertEquals("₹999", IndianMoney.rupees(new BigDecimal("999")));
        assertEquals("₹1,999", IndianMoney.rupees(new BigDecimal("1999")));
        assertEquals("₹12,345", IndianMoney.rupees(new BigDecimal("12345")));
        assertEquals("₹1,23,456", IndianMoney.rupees(new BigDecimal("123456")));
        assertEquals("₹1,00,00,000", IndianMoney.rupees(new BigDecimal("10000000")));
        assertEquals("₹1,750", IndianMoney.rupees(new BigDecimal("1749.6")));
        assertEquals("30", IndianMoney.percent(new BigDecimal("30.00")));
        assertEquals("12.5", IndianMoney.percent(new BigDecimal("12.50")));
        assertEquals("10", IndianMoney.percent(new BigDecimal("1E+1")));
    }

    @Test
    void packagePricesMatchTheHairOfferPoster() {
        // 30% off: ₹2,499 → ₹1,749 · ₹3,499 → ₹2,449 · ₹4,499 → ₹3,149 · ₹6,999 → ₹4,899 (from the reference posters)
        assertEquals(new BigDecimal("1749"), price("2499", "30"));
        assertEquals(new BigDecimal("2449"), price("3499", "30"));
        assertEquals(new BigDecimal("3149"), price("4499", "30"));
        assertEquals(new BigDecimal("3849"), price("5499", "30"));
        assertEquals(new BigDecimal("4899"), price("6999", "30"));
    }

    private BigDecimal price(String mrp, String pct) {
        return OfferCopy.packagePrice(pkg("x", pct, "Only", mrp)).price();
    }

    @Test
    void packageSumsItemsAndAppliesTheDiscountToTheTotal() {
        OfferCopy.PackagePrice p = OfferCopy.packagePrice(pkg("Bridal", "25", "Facial", "1500", "Hair spa", "2000", "Manicure", "500"));
        assertEquals(new BigDecimal("4000"), p.mrp());
        assertEquals(new BigDecimal("3000"), p.price());
    }

    @Test
    void tiersAreShownLowestSpendFirstRegardlessOfEntryOrder() {
        OfferCopy.Rendered r = OfferCopy.render(spend(tier("2999", "20", null), tier("999", "10", null)));
        assertEquals("Spend ₹999+ on any salon services → 10% OFF", r.lines().get(0));
        assertEquals("Spend ₹2,999+ on any salon services → 20% OFF", r.lines().get(1));
    }

    @Test
    void freeWithSpendNamesTheFreeItem() {
        OfferBlockDto b = free(tier("1999", null, "Nail Art on 5 Fingers"));
        b.setAppliesTo("any salon services");
        assertEquals("Spend ₹1,999+ on any salon services → Get FREE Nail Art on 5 Fingers",
                OfferCopy.render(b).lines().get(0));
    }

    @Test
    void captionHasEveryOfferValidityAndContact() {
        String caption = OfferCaptionBuilder.build(content(
                spend(tier("999", "10", null)),
                free(tier("1999", null, "Nail Art on 5 Fingers")),
                pkg("Bridal Glow", "30", "Facial", "2499", "Hair spa", "3499")));
        assertTrue(caption.contains("*Karwa Chauth & Diwali Beauty Bonus*"));
        assertTrue(caption.contains("Spend *₹999+* on any salon services → *10% OFF*"));
        assertTrue(caption.contains("get *FREE Nail Art on 5 Fingers*"));
        assertTrue(caption.contains("*Bridal Glow*"));
        assertTrue(caption.contains("~₹5,998~ → *₹4,199* (30% OFF)"));
        assertTrue(caption.contains("Valid till *15 Nov 2026*"));
        assertTrue(caption.contains("Alpine Pyramid Clubhouse"));
        assertTrue(caption.contains("*9731617930*"));
        assertTrue(caption.contains("T&C apply"));
    }

    @Test
    void promptStatesEveryFigureExactlyAndForbidsAnythingElse() {
        String prompt = PosterPromptBuilder.build(content(
                spend(tier("999", "10", null), tier("1999", "15", null)),
                free(tier("2999", null, "Basic Nail Art on 10 Fingers"))));
        assertTrue(prompt.contains("\"MYSTIC SALON\""));
        assertTrue(prompt.contains("\"Karwa Chauth & Diwali Beauty Bonus\""));
        assertTrue(prompt.contains("Spend ₹999+ on any salon services — 10% OFF"));
        assertTrue(prompt.contains("Spend ₹1,999+ on any salon services — 15% OFF"));
        assertTrue(prompt.contains("Get FREE Basic Nail Art on 10 Fingers"));
        assertTrue(prompt.contains("Valid till 15 November 2026"));
        assertTrue(prompt.contains("9731617930"));
        assertTrue(prompt.contains("Salon Services Include"));
        assertTrue(prompt.contains("STRICT RULES"));
        assertTrue(prompt.contains("vertical portrait"));
        // The number appears once in the spec, plus once in the instruction to write it only once.
        assertEquals(1, prompt.split("9731617930", -1).length - 1);
    }

    @Test
    void promptHandlesAPackageAndSquareFormat() {
        OfferContent c = content(pkg("Bridal Glow", "30", "Facial", "2499", "Hair spa", "3499"));
        OfferContent square = new OfferContent(c.occasion(), c.style(), PosterFormat.SQUARE, c.brandName(), c.headline(),
                null, null, null, null, null, c.blocks(), List.of(), "include a bride");
        String prompt = PosterPromptBuilder.build(square);
        assertTrue(prompt.contains("square (1:1)"));
        assertTrue(prompt.contains("Package price ₹4,199"));
        assertTrue(prompt.contains("include a bride"));
        assertFalse(prompt.contains("Valid till"));
        assertFalse(prompt.contains("Salon Services Include"));
    }
}
