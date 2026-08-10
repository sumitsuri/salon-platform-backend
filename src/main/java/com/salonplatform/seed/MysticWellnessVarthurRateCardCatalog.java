package com.salonplatform.seed;

import java.util.ArrayList;
import java.util.List;

/**
 * Mystic Wellness — Varthur branch (MW01) printed menu.
 * Transcribed from the Varthur Main Road rate card (SLV Sunrise, Bangalore).
 */
public final class MysticWellnessVarthurRateCardCatalog {

    private MysticWellnessVarthurRateCardCatalog() {}

    public static List<RateCardCatalog.TopCategoryDef> all() {
        List<RateCardCatalog.TopCategoryDef> tops = new ArrayList<>();
        tops.add(men());
        tops.add(women());
        tops.add(shared());
        tops.add(spa());
        return tops;
    }

    private static RateCardCatalog.TopCategoryDef men() {
        return new RateCardCatalog.TopCategoryDef("Men", 1, List.of(
                sub("Hair Cut & Styling", 1, List.of(
                        s("Baby Hair Cut", 150, 20),
                        s("Hair Cut", 250, 30),
                        s("Advance Cut", 300, 40),
                        s("Change Of Style", 350, 45)
                )),
                sub("Shave & Beard Grooming", 2, List.of(
                        s("Clean Shave", 100, 15),
                        s("Beard Trimming", 100, 15),
                        s("Beard Trimming + Styling", 150, 25)
                )),
                sub("Hair Colour", 3, List.of(
                        s("L'Oreal Majirel Colour", 800, 60),
                        s("L'Oreal Inoa Colour", 900, 75),
                        s("Global Colour", 1000, 90),
                        s("Advance Colour", 2000, 120),
                        s("Highlight Per Streak", 150, 20)
                )),
                sub("Head · Neck · Shoulder Massage", 4, List.of(
                        s("Coconut Oil Massage (30 min)", 400, 30),
                        s("Almond Oil Massage (30 min)", 400, 30),
                        s("Olive Oil Massage (30 min)", 450, 30),
                        s("Mythic Oil L'Oreal Massage (30 min)", 500, 30)
                )),
                sub("Healthy Hair Spa", 5, List.of(
                        s("Smooth Hair Spa", 700, 60),
                        s("Hydra Hair Spa", 800, 60),
                        s("Protein Hair Spa", 800, 60),
                        s("Repair Hair Spa", 1000, 60),
                        s("Anti Dandruff Clear Dose (add-on)", 300, 15),
                        s("Clear Dose Only", 500, 20)
                )),
                sub("Waxing", 6, List.of(
                        s("Under Arms Wax", 200, 15),
                        s("Chest Wax", 500, 25),
                        s("Back Wax", 850, 40),
                        s("Full Body Wax", 3000, 90)
                ))
        ));
    }

    private static RateCardCatalog.TopCategoryDef women() {
        return new RateCardCatalog.TopCategoryDef("Women", 2, List.of(
                sub("Threading", 1, List.of(
                        s("Eyebrow Threading", 50, 10),
                        s("Upper Lip Threading", 40, 5),
                        s("Lower Lip Threading", 30, 5),
                        s("Chin Threading", 40, 5),
                        s("Forehead Threading", 40, 5),
                        s("Sides Threading", 60, 10),
                        s("Full Face Threading", 250, 20)
                )),
                sub("Hair Cut & Styling", 2, List.of(
                        s("Hair Wash", 250, 20),
                        s("Baby Hair Cut", 250, 20),
                        s("Blow Dry & Style", 500, 45),
                        s("Basic Hair Cut", 500, 40),
                        s("Step Cut", 700, 50),
                        s("Layer Cut", 700, 50),
                        s("Advance Cut", 900, 60)
                )),
                sub("Head · Neck · Shoulder Massage", 3, List.of(
                        s("Almond Oil Massage (30 min)", 500, 30),
                        s("Coconut Oil Massage (30 min)", 500, 30),
                        s("Olive Oil Massage (30 min)", 500, 30),
                        s("Mythic Oil L'Oreal Massage (30 min)", 750, 30)
                )),
                sub("Healthy Hair Spa", 4, List.of(
                        s("L'Oreal Hair Spa (from)", 1000, 75),
                        s("Hydra Hair Spa (from)", 1100, 75),
                        s("Protein Hair Spa (from)", 1200, 75),
                        s("Repair Hair Spa (from)", 1300, 90),
                        s("Anti Dandruff Clear Dose (add-on)", 300, 15),
                        s("Clear Dose Only", 500, 20)
                )),
                sub("Hair Texture", 5, List.of(
                        s("Smoothening (from)", 4000, 150),
                        s("Keratine (from)", 5000, 180),
                        s("Botox Treatment (from)", 6000, 180),
                        s("Hair Ironing (Temporary) (from)", 500, 45),
                        s("Tong Curls (Temporary) (from)", 600, 45)
                )),
                sub("Hair Colour · Majirel", 6, List.of(
                        s("Root Touch-up (Majirel)", 1000, 60),
                        s("Global Colour (Majirel)", 2500, 90),
                        s("Balayage / Ombre (Majirel)", 3000, 120),
                        s("Streaks (Majirel)", 300, 20)
                )),
                sub("Hair Colour · Inoa", 7, List.of(
                        s("Root Touch-up (Inoa)", 1200, 75),
                        s("Global Colour (Inoa) (from)", 2800, 90)
                )),
                sub("Waxing · Honey", 8, List.of(
                        s("Upper Lip Wax (Honey)", 60, 10),
                        s("Chin Wax (Honey)", 60, 10),
                        s("Sides Wax (Honey)", 80, 10),
                        s("Full Face Wax (Honey)", 300, 25),
                        s("Under Arms Wax (Honey)", 100, 15),
                        s("Half Arms Wax (Honey)", 200, 20),
                        s("Full Arms Wax (Honey)", 300, 30),
                        s("Half Legs Wax (Honey)", 300, 30),
                        s("Full Legs Wax (Honey)", 400, 45),
                        s("Stomach Wax (Honey)", 300, 25),
                        s("Full Front Wax (Honey)", 400, 35),
                        s("Full Back Wax (Honey)", 500, 40),
                        s("Bikini Wax (Honey)", 1300, 40),
                        s("Full Body Wax (Honey)", 1800, 90)
                )),
                sub("Waxing · Rica", 9, List.of(
                        s("Upper Lip Wax (Rica)", 80, 10),
                        s("Chin Wax (Rica)", 80, 10),
                        s("Sides Wax (Rica)", 100, 10),
                        s("Full Face Wax (Rica)", 400, 25),
                        s("Under Arms Wax (Rica)", 150, 15),
                        s("Half Arms Wax (Rica)", 250, 20),
                        s("Full Arms Wax (Rica)", 500, 30),
                        s("Half Legs Wax (Rica)", 500, 30),
                        s("Full Legs Wax (Rica)", 600, 45),
                        s("Stomach Wax (Rica)", 400, 25),
                        s("Full Front Wax (Rica)", 500, 35),
                        s("Full Back Wax (Rica)", 600, 40),
                        s("Bikini Wax (Rica)", 1500, 40),
                        s("Full Body Wax (Rica)", 2000, 90)
                ))
        ));
    }

    private static RateCardCatalog.TopCategoryDef shared() {
        return new RateCardCatalog.TopCategoryDef("Shared", 3, List.of(
                sub("Cleanup & Facial", 1, List.of(
                        s("Fruit Cleanup", 600, 35),
                        s("Fruit Facial", 1000, 45),
                        s("VLCC Skin Lighting & Glow Facial", 1200, 50),
                        s("Aroma Gold Facial", 1600, 55),
                        s("Red Wine Cleanup", 800, 35),
                        s("Red Wine Facial", 1600, 55),
                        s("De-Tan Facial", 1600, 60),
                        s("O3+ Shine & Glow Facial", 1900, 60),
                        s("O3+ Power Brightening Facial", 2100, 70),
                        s("O3+ Bridal Facial", 3500, 90)
                )),
                sub("Manicure", 2, List.of(
                        s("Normal Manicure", 400, 35),
                        s("Aroma Manicure", 600, 35),
                        s("Vedic De Tan Manicure", 700, 35),
                        s("Ragga Manicure", 700, 35),
                        s("Spa Manicure", 800, 40),
                        s("AVL Manicure", 900, 45)
                )),
                sub("Pedicure", 3, List.of(
                        s("Normal Pedicure", 500, 45),
                        s("Aroma Pedicure", 700, 45),
                        s("Vedic De Tan Pedicure", 700, 50),
                        s("Ragga Pedicure", 900, 50),
                        s("Spa Pedicure", 900, 50),
                        s("AVL Pedicure", 1200, 60)
                ))
        ));
    }

    private static RateCardCatalog.TopCategoryDef spa() {
        return new RateCardCatalog.TopCategoryDef("Spa", 4, List.of(
                sub("Body Skin Care", 1, List.of(
                        s("Almond Scrub", 1500, 45),
                        s("Coffee Scrub", 1700, 45),
                        s("Fruit Polishing", 2000, 50),
                        s("Chocolate In Wine Polishing", 3000, 60)
                )),
                sub("Mini Spa (30 min)", 2, List.of(
                        s("Back Massage (30 min)", 700, 30),
                        s("Foot Massage (30 min)", 500, 30),
                        s("Hand Massage (30 min)", 500, 30),
                        s("Full Leg Massage (30 min)", 1000, 30),
                        s("Face Massage (30 min)", 500, 30)
                )),
                sub("SPA · 60 min", 3, List.of(
                        s("Swedish Massage (60 min)", 1800, 60),
                        s("Aroma Massage (60 min)", 1900, 60),
                        s("Deep Tissue Massage (60 min)", 2000, 60),
                        s("Balinese Massage (60 min)", 2200, 60),
                        s("Thai Massage (60 min)", 2100, 60)
                )),
                sub("SPA · 90 min", 4, List.of(
                        s("Swedish Massage (90 min)", 2700, 90),
                        s("Aroma Massage (90 min)", 2800, 90),
                        s("Deep Tissue Massage (90 min)", 3000, 90),
                        s("Balinese Massage (90 min)", 3200, 90),
                        s("Thai Massage (90 min)", 3000, 90)
                )),
                sub("SPA Package · 5 Sittings / 4 Months", 5, List.of(
                        s("Swedish SPA Package — 5 Sittings (60 min)", 7000, 60),
                        s("Aroma SPA Package — 5 Sittings (60 min)", 7500, 60),
                        s("Deep Tissue SPA Package — 5 Sittings (60 min)", 8000, 60),
                        s("Thai SPA Package — 5 Sittings (60 min)", 8000, 60),
                        s("Balinese SPA Package — 5 Sittings (60 min)", 8500, 60)
                )),
                sub("SPA Package · 10 Sittings / 6 Months", 6, List.of(
                        s("Swedish SPA Package — 10 Sittings (60 min)", 12000, 60),
                        s("Aroma SPA Package — 10 Sittings (60 min)", 13000, 60),
                        s("Deep Tissue SPA Package — 10 Sittings (60 min)", 14000, 60),
                        s("Thai SPA Package — 10 Sittings (60 min)", 14000, 60),
                        s("Balinese SPA Package — 10 Sittings (60 min)", 15000, 60)
                ))
        ));
    }

    private static RateCardCatalog.SubCategoryDef sub(String name, int sort, List<RateCardCatalog.ServiceDef> services) {
        return new RateCardCatalog.SubCategoryDef(name, sort, services);
    }

    private static RateCardCatalog.ServiceDef s(String name, int price, int duration) {
        return new RateCardCatalog.ServiceDef(name, price, duration);
    }
}
