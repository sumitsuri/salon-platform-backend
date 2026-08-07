package com.salonplatform.seed;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Mystic Ocean Salon rate card — Men / Women / Kids / Shared hierarchy.
 * Prices are list rates (INR). Branch multipliers applied at seed time.
 */
public final class RateCardCatalog {

    private RateCardCatalog() {}

    public record ServiceDef(String name, int price, int durationMinutes, boolean variablePricing) {
        ServiceDef(String name, int price) {
            this(name, price, 30, inferVariablePricing(name));
        }

        ServiceDef(String name, int price, int durationMinutes) {
            this(name, price, durationMinutes, inferVariablePricing(name));
        }
    }

    /** Services priced "from X" / "onwards" allow extra amount at billing time. */
    public static boolean inferVariablePricing(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.contains("(from)") || lower.contains("onwards");
    }

    public record SubCategoryDef(String name, int sortOrder, List<ServiceDef> services) {}

    public record TopCategoryDef(String name, int sortOrder, List<SubCategoryDef> subs) {}

    public static List<TopCategoryDef> all() {
        List<TopCategoryDef> tops = new ArrayList<>();
        tops.add(men());
        tops.add(women());
        tops.add(kids());
        tops.add(shared());
        return tops;
    }

    /** Tenant-specific rate card — Velvet Scissors and Mystic Wellness use the PDF catalog. */
    public static List<TopCategoryDef> forTenantSlug(String tenantSlug) {
        if ("velvet-scissors".equalsIgnoreCase(tenantSlug)
                || "demo-brand".equalsIgnoreCase(tenantSlug)
                || "mystic-wellness".equalsIgnoreCase(tenantSlug)) {
            return VelvetScissorsRateCardCatalog.all();
        }
        return all();
    }

    private static TopCategoryDef men() {
        return new TopCategoryDef("Men", 1, List.of(
                sub("Hair Cut & Styling", 1, List.of(
                        s("Haircut", 199, 30),
                        s("Advanced Cut", 249, 40),
                        s("Change of Style", 349, 45)
                )),
                sub("Shave & Beard Grooming", 2, List.of(
                        s("Clean Shave", 149, 15),
                        s("Beard Trimming", 149, 15),
                        s("Beard Trimming + Styling", 199, 25)
                )),
                sub("Hair Colour", 3, List.of(
                        s("Schwarzkopf Colour", 649, 60),
                        s("Loreal Majirel Colour", 649, 60),
                        s("Loreal Inoa Colour", 799, 75),
                        s("Schwarzkopf Ammonia Free Colour", 799, 75)
                )),
                sub("Head · Neck · Shoulder Massage", 4, List.of(
                        s("Coconut Oil Massage (30 min)", 299, 30),
                        s("Almond Oil Massage (30 min)", 299, 30),
                        s("Olive Oil Massage (30 min)", 399, 30),
                        s("Mythic Oil Loreal Massage (30 min)", 499, 30)
                )),
                sub("Healthy Hair Spa", 5, List.of(
                        s("Smooth Hair Spa (Loreal)", 699, 60),
                        s("Hydra Hair Spa", 799, 60),
                        s("Protein Hair Spa", 899, 60),
                        s("Repair Hair Spa", 999, 60),
                        s("Herbal Aromatherapy Spa", 1099, 60),
                        s("Anti-Dandruff Clear Dose (add-on)", 299, 15),
                        s("Clear Dose Only", 499, 20)
                )),
                sub("Waxing", 6, List.of(
                        s("Under Arms Wax", 149, 15),
                        s("Chest Wax", 499, 30),
                        s("Back Wax", 799, 40),
                        s("Full Body Wax", 2999, 90)
                ))
        ));
    }

    private static TopCategoryDef women() {
        return new TopCategoryDef("Women", 2, List.of(
                sub("Threading", 1, List.of(
                        s("Eyebrow Threading", 49, 10),
                        s("Upper Lip Threading", 39, 5),
                        s("Lower Lip Threading", 29, 5),
                        s("Chin Threading", 39, 5),
                        s("Forehead Threading", 39, 5),
                        s("Sides Threading", 59, 10),
                        s("Full Face Threading", 249, 20)
                )),
                sub("Hair Cut & Styling", 2, List.of(
                        s("Hair Wash (Loreal)", 249, 20),
                        s("Hair Wash (Keratin)", 299, 20),
                        s("Blow Dry & Style", 449, 30),
                        s("Basic Hair Cut", 499, 40),
                        s("Step Cut", 799, 50),
                        s("Layer Cut", 799, 50),
                        s("Advance Cut", 999, 60)
                )),
                sub("Head · Neck · Shoulder Massage", 3, List.of(
                        s("Almond Oil Massage (30 min)", 399, 30),
                        s("Coconut Oil Massage (30 min)", 449, 30),
                        s("Olive Oil Massage (30 min)", 499, 30),
                        s("Mythic Oil Loreal Massage (30 min)", 749, 30)
                )),
                sub("Healthy Hair Spa", 4, List.of(
                        s("Anti-Dandruff Clear Dose (add-on)", 299, 15),
                        s("Clear Dose Only", 499, 20),
                        s("Loreal Hair Spa — Shoulder", 999, 75),
                        s("Loreal Hair Spa — Mid Back", 1099, 75),
                        s("Loreal Hair Spa — Waist", 1199, 90),
                        s("Hydra Hair Spa — Shoulder", 1299, 75),
                        s("Hydra Hair Spa — Mid Back", 1399, 75),
                        s("Hydra Hair Spa — Waist", 1699, 90)
                )),
                sub("Hair Texture", 5, List.of(
                        s("Smoothening — Shoulder", 3999, 150),
                        s("Smoothening — Mid Back", 4499, 165),
                        s("Smoothening — Waist", 4999, 180),
                        s("Keratin — Shoulder", 4999, 180),
                        s("Keratin — Mid Back", 5499, 195),
                        s("Keratin — Waist", 5999, 210),
                        s("Botox Treatment — Shoulder (from)", 5999, 180),
                        s("Botox Treatment — Mid Back (from)", 6499, 195),
                        s("Botox Treatment — Waist (from)", 6999, 210),
                        s("Hair Ironing (Temporary) — Shoulder", 499, 45),
                        s("Hair Ironing (Temporary) — Mid Back", 599, 50),
                        s("Hair Ironing (Temporary) — Waist (from)", 699, 60),
                        s("Tong Curls (Temporary) — Shoulder", 599, 45),
                        s("Tong Curls (Temporary) — Mid Back", 699, 50),
                        s("Tong Curls (Temporary) — Waist (from)", 799, 60)
                )),
                sub("Hair Colour", 6, List.of(
                        s("Root Touch-up", 1199, 60),
                        s("Root Touch-up (premium)", 1399, 75),
                        s("Global Colour Majirel — Shoulder", 2499, 90),
                        s("Global Colour Majirel — Mid Back", 3499, 105),
                        s("Global Colour Majirel — Waist (from)", 4499, 120),
                        s("Global Colour Inoa — Shoulder", 2699, 90),
                        s("Global Colour Inoa — Mid Back", 3699, 105),
                        s("Global Colour Inoa — Waist (from)", 4699, 120),
                        s("Balayage / Ombre — Shoulder", 3499, 120),
                        s("Balayage / Ombre — Mid Back", 4499, 135),
                        s("Balayage / Ombre — Waist (from)", 5499, 150),
                        s("Per Streak — Shoulder", 299, 20),
                        s("Per Streak — Mid Back", 399, 25),
                        s("Per Streak — Waist (from)", 499, 30),
                        s("Loreal Majirel Colour", 999, 75),
                        s("Loreal Inoa Colour", 1199, 90),
                        s("Schwarzkopf Colour", 999, 75),
                        s("Schwarzkopf Ammonia Free Colour", 1199, 90)
                )),
                sub("Waxing · Honey", 7, List.of(
                        s("Upper Lip Wax (Honey)", 79, 10),
                        s("Chin Wax (Honey)", 79, 10),
                        s("Sides Wax (Honey)", 99, 10),
                        s("Full Face Wax (Honey)", 399, 25),
                        s("Under Arms Wax (Honey)", 149, 15),
                        s("Half Arms Wax (Honey)", 249, 20),
                        s("Full Arms Wax (Honey)", 449, 30),
                        s("Half Legs Wax (Honey)", 349, 30),
                        s("Full Legs Wax (Honey)", 599, 45),
                        s("Stomach Wax (Honey)", 399, 25),
                        s("Full Front Wax (Honey)", 499, 35),
                        s("Full Back Wax (Honey)", 599, 40),
                        s("Bikini Wax (Honey)", 1399, 40),
                        s("Full Body Wax (Honey)", 2199, 90)
                )),
                sub("Waxing · Rica", 8, List.of(
                        s("Upper Lip Wax (Rica)", 59, 10),
                        s("Chin Wax (Rica)", 59, 10),
                        s("Sides Wax (Rica)", 79, 10),
                        s("Full Face Wax (Rica)", 299, 25),
                        s("Under Arms Wax (Rica)", 99, 15),
                        s("Half Arms Wax (Rica)", 199, 20),
                        s("Full Arms Wax (Rica)", 299, 30),
                        s("Half Legs Wax (Rica)", 249, 30),
                        s("Full Legs Wax (Rica)", 399, 45),
                        s("Stomach Wax (Rica)", 299, 25),
                        s("Full Front Wax (Rica)", 399, 35),
                        s("Full Back Wax (Rica)", 499, 40),
                        s("Bikini Wax (Rica)", 1049, 40),
                        s("Full Body Wax (Rica)", 1599, 90)
                ))
        ));
    }

    private static TopCategoryDef kids() {
        return new TopCategoryDef("Kids", 3, List.of(
                sub("Hair Cut & Styling", 1, List.of(
                        s("Girl Baby Haircut (Below 5 yrs)", 249, 20),
                        s("Boy Baby Haircut (Below 5 yrs)", 149, 25)
                ))
        ));
    }

    private static TopCategoryDef shared() {
        return new TopCategoryDef("Shared", 4, List.of(
                sub("D-Tan & Bleach", 1, List.of(
                        s("Face & Neck D-Tan / Bleach", 499, 30),
                        s("Under Arms D-Tan / Bleach", 149, 15),
                        s("Half Arms D-Tan / Bleach", 399, 25),
                        s("Full Arms D-Tan / Bleach", 499, 35),
                        s("Half Legs D-Tan / Bleach", 499, 35),
                        s("Full Legs D-Tan / Bleach", 599, 45),
                        s("Full Back D-Tan / Bleach", 599, 40),
                        s("Full Front D-Tan / Bleach", 599, 40),
                        s("Stomach D-Tan / Bleach", 299, 20),
                        s("Full Body D-Tan / Bleach", 1999, 90)
                )),
                sub("Facial", 2, List.of(
                        s("Fruit Facial", 799, 45),
                        s("Lotus Facial", 899, 45),
                        s("Sara Fruit Facial", 999, 50),
                        s("VLCC Skin Lighting & Glow Facial", 1199, 50),
                        s("Red Wine Facial", 1299, 55),
                        s("Aroma Gold Facial", 1399, 55),
                        s("D-Tan Facial", 1599, 60),
                        s("O3+ Shine & Glow Facial", 1699, 60),
                        s("O3+ Power Brightening Facial", 1999, 70),
                        s("Skin Miracle Whitening Facial", 2499, 75),
                        s("O3+ Bridal Facial", 2999, 90),
                        s("Sara Gold & Diamond Facial", 1499, 60),
                        s("Raaga Gold Facial", 1599, 60)
                )),
                sub("Cleanup", 3, List.of(
                        s("Fruit Cleanup", 599, 30),
                        s("Lotus Cleanup", 699, 30),
                        s("VLCC Cleanup", 899, 35),
                        s("Raaga Cleanup", 799, 35)
                )),
                sub("Pedicure", 4, List.of(
                        s("Normal Pedicure", 499, 45),
                        s("Aroma Pedicure", 599, 45),
                        s("Vedic D-Tan Pedicure", 699, 50),
                        s("H & F Pedicure", 899, 50),
                        s("AVL Pedicure", 1299, 60),
                        s("Sara Pedicure", 799, 50),
                        s("Raaga Pedicure", 899, 50),
                        s("Pedilogix O3+ Pedicure", 1299, 60),
                        s("Lotus Crystal Spa Pedicure", 1099, 55),
                        s("Bombani Pedicure", 1199, 55)
                )),
                sub("Manicure", 5, List.of(
                        s("Normal Manicure", 399, 30),
                        s("Aroma Manicure", 499, 30),
                        s("Vedic D-Tan Manicure", 599, 35),
                        s("H & F Manicure", 799, 35),
                        s("AVL Manicure", 999, 45),
                        s("Sara Manicure", 599, 35),
                        s("Raaga Manicure", 699, 35),
                        s("Pedilogix O3+ Manicure", 999, 45),
                        s("Lotus Crystal Spa Manicure", 1399, 45),
                        s("Bombani Manicure", 1599, 45),
                        s("Cut & Filing", 149, 15),
                        s("Nail Polish", 99, 10)
                )),
                sub("Massage & Care", 6, List.of(
                        s("Foot Massage (30 min)", 499, 30),
                        s("Back Massage (30 min)", 799, 30),
                        s("Face Massage (30 min)", 399, 30),
                        s("Hands & Feet Avalon Care", 899, 40)
                ))
        ));
    }

    private static SubCategoryDef sub(String name, int sort, List<ServiceDef> services) {
        return new SubCategoryDef(name, sort, services);
    }

    private static ServiceDef s(String name, int price, int duration) {
        return new ServiceDef(name, price, duration);
    }

    public static BigDecimal money(int rupees) {
        return BigDecimal.valueOf(rupees);
    }
}
