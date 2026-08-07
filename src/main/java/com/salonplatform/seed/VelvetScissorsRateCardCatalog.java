package com.salonplatform.seed;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Velvet Scissors rate card — transcribed from final.pdf (Mystic Ocean Salon layout).
 * Men / Women / Kids / Shared hierarchy with PDF list prices (INR).
 */
public final class VelvetScissorsRateCardCatalog {

    private VelvetScissorsRateCardCatalog() {}

    public static List<RateCardCatalog.TopCategoryDef> all() {
        List<RateCardCatalog.TopCategoryDef> tops = new ArrayList<>();
        tops.add(men());
        tops.add(women());
        tops.add(kids());
        tops.add(shared());
        return tops;
    }

    private static RateCardCatalog.TopCategoryDef men() {
        return new RateCardCatalog.TopCategoryDef("Men", 1, List.of(
                sub("Hair Cut & Styling", 1, List.of(
                        s("Haircut", 199),
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
                        s("Coconut Oil Massage (30 min)", 399),
                        s("Almond Oil Massage (30 min)", 399),
                        s("Olive Oil Massage (30 min)", 399),
                        s("Mythic Oil Loreal Massage (30 min)", 499)
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
                        s("Under Arms Wax", 199, 15),
                        s("Chest Wax", 599),
                        s("Back Wax", 799, 40),
                        s("Full Body Wax", 2999, 90)
                ))
        ));
    }

    private static RateCardCatalog.TopCategoryDef women() {
        return new RateCardCatalog.TopCategoryDef("Women", 2, List.of(
                sub("Threading", 1, List.of(
                        s("Eyebrow Threading", 59, 10),
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
                        s("Blow Dry & Style", 449),
                        s("Basic Hair Cut", 499, 40),
                        s("Step Cut", 799, 50),
                        s("Layer Cut", 799, 50),
                        s("Advance Cut", 999, 60)
                )),
                sub("Head · Neck · Shoulder Massage", 3, List.of(
                        s("Almond Oil Massage (30 min)", 499),
                        s("Coconut Oil Massage (30 min)", 499),
                        s("Olive Oil Massage (30 min)", 499),
                        s("Mythic Oil Loreal Massage (30 min)", 749)
                )),
                sub("Healthy Hair Spa", 4, List.of(
                        s("Anti-Dandruff Clear Dose (add-on)", 299, 15),
                        s("Clear Dose Only", 499, 20),
                        s("Loreal Hair Spa — Shoulder", 999, 75),
                        s("Loreal Hair Spa — Mid Back", 1099, 75),
                        s("Loreal Hair Spa — Waist", 1199, 90),
                        s("Hydra Hair Spa — Shoulder", 1099, 75),
                        s("Hydra Hair Spa — Mid Back", 1199, 75),
                        s("Hydra Hair Spa — Waist", 1299, 90),
                        s("Protein Hair Spa — Shoulder", 1199, 75),
                        s("Protein Hair Spa — Mid Back", 1299, 75),
                        s("Protein Hair Spa — Waist", 1399, 90),
                        s("Repair Hair Spa — Shoulder", 1299, 75),
                        s("Repair Hair Spa — Mid Back", 1399, 75),
                        s("Repair Hair Spa — Waist", 1499, 90),
                        s("Herbal Aromatherapy Spa — Shoulder", 1399, 75),
                        s("Herbal Aromatherapy Spa — Mid Back", 1499, 75),
                        s("Herbal Aromatherapy Spa — Waist", 1599, 90),
                        s("Keratin Hair Spa — Shoulder", 1699, 90),
                        s("Keratin Hair Spa — Mid Back", 1799, 90),
                        s("Keratin Hair Spa — Waist", 1899, 90)
                )),
                sub("Hair Texture", 5, List.of(
                        s("Smoothening — Shoulder", 3999, 150),
                        s("Smoothening — Mid Back", 4999, 165),
                        s("Smoothening — Waist", 5999, 180),
                        s("Keratin — Shoulder", 4499, 180),
                        s("Keratin — Mid Back", 5499, 195),
                        s("Keratin — Waist", 6499, 210),
                        s("Botox Treatment — Shoulder (from)", 4999, 180),
                        s("Botox Treatment — Mid Back (from)", 5999, 195),
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
                        s("Per Streak — Waist (from)", 499),
                        s("Loreal Majirel Colour", 999, 75),
                        s("Loreal Inoa Colour", 1199, 90),
                        s("Schwarzkopf Colour", 1199, 75),
                        s("Schwarzkopf Ammonia Free Colour", 1399, 90)
                )),
                sub("Waxing · Honey", 7, List.of(
                        s("Upper Lip Wax (Honey)", 59, 10),
                        s("Chin Wax (Honey)", 59, 10),
                        s("Sides Wax (Honey)", 79, 10),
                        s("Full Face Wax (Honey)", 299, 25),
                        s("Under Arms Wax (Honey)", 149, 15),
                        s("Half Arms Wax (Honey)", 249, 20),
                        s("Full Arms Wax (Honey)", 349),
                        s("Half Legs Wax (Honey)", 299),
                        s("Full Legs Wax (Honey)", 349, 45),
                        s("Stomach Wax (Honey)", 349, 25),
                        s("Full Front Wax (Honey)", 449, 35),
                        s("Full Back Wax (Honey)", 549, 40),
                        s("Bikini Wax (Honey)", 1099, 40),
                        s("Full Body Wax (Honey)", 1649, 90)
                )),
                sub("Waxing · Rica", 8, List.of(
                        s("Upper Lip Wax (Rica)", 99, 10),
                        s("Chin Wax (Rica)", 99, 10),
                        s("Sides Wax (Rica)", 99, 10),
                        s("Full Face Wax (Rica)", 399, 25),
                        s("Under Arms Wax (Rica)", 199, 15),
                        s("Half Arms Wax (Rica)", 299, 20),
                        s("Full Arms Wax (Rica)", 499),
                        s("Half Legs Wax (Rica)", 399),
                        s("Full Legs Wax (Rica)", 649, 45),
                        s("Stomach Wax (Rica)", 449, 25),
                        s("Full Front Wax (Rica)", 599, 35),
                        s("Full Back Wax (Rica)", 649, 40),
                        s("Bikini Wax (Rica)", 1399, 40),
                        s("Full Body Wax (Rica)", 2399, 90)
                ))
        ));
    }

    private static RateCardCatalog.TopCategoryDef kids() {
        return new RateCardCatalog.TopCategoryDef("Kids", 3, List.of(
                sub("Hair Cut & Styling", 1, List.of(
                        s("Baby Haircut (Below 5 yrs)", 249, 20),
                        s("Kids Haircut", 149, 25),
                        s("Kids Advanced Cut", 249)
                ))
        ));
    }

    private static RateCardCatalog.TopCategoryDef shared() {
        return new RateCardCatalog.TopCategoryDef("Shared", 4, List.of(
                sub("D-Tan & Bleach", 1, List.of(
                        s("Face & Neck D-Tan / Bleach", 499),
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
                        s("O3+ Shine & Glow Facial", 1799, 60),
                        s("O3+ Power Brightening Facial", 1999, 70),
                        s("Korean Glass Facial", 2599, 75),
                        s("O3+ Bridal Facial", 2999, 90),
                        s("Sara Gold & Diamond Facial", 1499, 60),
                        s("Raaga Gold Facial", 1599, 60)
                )),
                sub("Cleanup", 3, List.of(
                        s("Fruit Cleanup", 599),
                        s("Lotus Cleanup", 699),
                        s("VLCC Cleanup", 899, 35),
                        s("Raaga Cleanup", 799, 35)
                )),
                sub("Pedicure", 4, List.of(
                        s("Normal Pedicure", 499, 45),
                        s("Aroma Pedicure", 699, 45),
                        s("Vedic D-Tan Pedicure", 999, 50),
                        s("Foot Massage (30 min)", 499),
                        s("Sara Pedicure", 799, 50),
                        s("Raaga Pedicure", 899, 50),
                        s("Pedilogix O3+ Pedicure", 1299, 60),
                        s("Lotus Crystal Spa Pedicure", 1099, 55),
                        s("Bombani Pedicure", 1199, 55)
                )),
                sub("Manicure", 5, List.of(
                        s("Normal Manicure", 399),
                        s("Aroma Manicure", 599),
                        s("Vedic D-Tan Manicure", 799, 35),
                        s("Sara Manicure", 599, 35),
                        s("Raaga Manicure", 699, 35),
                        s("Pedilogix O3+ Manicure", 999, 45),
                        s("Lotus Crystal Spa Manicure", 1399, 45),
                        s("Bombani Manicure", 1599, 45),
                        s("Cut & Filing", 149, 15),
                        s("Nail Polish", 99, 10)
                )),
                sub("Massage & Care", 6, List.of(
                        s("Back Massage (30 min)", 799),
                        s("Face Massage (30 min)", 399),
                        s("Hands & Feet Avalon Care", 899, 40)
                ))
        ));
    }

    private static RateCardCatalog.SubCategoryDef sub(String name, int sort, List<RateCardCatalog.ServiceDef> services) {
        return new RateCardCatalog.SubCategoryDef(name, sort, services);
    }

    private static RateCardCatalog.ServiceDef s(String name, int price, int duration) {
        return new RateCardCatalog.ServiceDef(name, price, duration);
    }

    private static RateCardCatalog.ServiceDef s(String name, int price) {
        return new RateCardCatalog.ServiceDef(name, price);
    }

    public static BigDecimal money(int rupees) {
        return RateCardCatalog.money(rupees);
    }
}
