package com.salonplatform.seed;

import com.salonplatform.domain.enums.SalonTier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Shared definitions for demo / competitor tenants used by seeders.
 * Keep login emails stable — see docs/DEMO_CREDENTIALS.md.
 */
public final class SeedCatalog {

    private SeedCatalog() {}

    public record StaffSeed(
            String name,
            String skills,
            String biometricId,
            String salary,
            LocalDate joiningDate,
            boolean idProofCollected,
            String idProofReference,
            String monthlySalesTarget,
            String incentivePercent
    ) {}

    public record BranchSeed(
            String name,
            String code,
            String address,
            String societyDefault,
            String gstin,
            String phone,
            double latitude,
            double longitude,
            String monthlySalesTarget,
            String managerName,
            String managerEmail,
            List<StaffSeed> staff
    ) {}

    public record TenantSeed(
            String name,
            String slug,
            String primaryColor,
            String adminName,
            String adminEmail,
            String adminPassword,
            /** Multiplier applied to Haircut / Beard / Color (Facial stays fixed). */
            BigDecimal priceMultiplier,
            /** Booking volume scale vs baseline (1.0 = demo-brand typical). */
            double bookingVolumeScale,
            SalonTier salonTier,
            List<BranchSeed> branches
    ) {}

    public static final List<TenantSeed> TENANTS = List.of(
            demoSalonBrand(),
            velvetScissors(),
            bloomBeauty(),
            crownAndComb(),
            silkAndShear()
    );

    public static List<String> slugs() {
        return TENANTS.stream().map(TenantSeed::slug).toList();
    }

    private static TenantSeed demoSalonBrand() {
        return new TenantSeed(
                "Demo Salon Brand",
                "demo-brand",
                "#7c3aed",
                "Brand CEO",
                "ceo@demo-brand.local",
                "ceo123",
                BigDecimal.ONE,
                1.0,
                SalonTier.MID_MARKET,
                List.of(
                        branch("Mantri Lithos", "LIT", "Mantri Lithos, Bangalore", "Mantri Lithos",
                                "29AABCU9603R1ZM", "9876543210", 12.9352, 77.6245, "400000",
                                "Lithos Manager", "manager.lithos@demo-brand.local",
                                List.of(
                                        staff("Amit", "Hair,Grooming", "FP-AMIT-LITHOS", "25000",
                                                LocalDate.of(2024, 3, 1), true, "Aadhaar XXXX4521", "120000", "5"),
                                        staff("Priya", "Skin,Hair", "FP-PRIYA-LITHOS", "28000",
                                                LocalDate.of(2023, 8, 15), true, "PAN XXXX7890", "150000", "5")
                                )),
                        branch("Mantri Webcity", "WEB", "Mantri Webcity, Bangalore", "Mantri Webcity",
                                "29AABCU9603R1ZN", "9876543211", 12.9716, 77.5946, "350000",
                                "Webcity Manager", "manager.webcity@demo-brand.local",
                                List.of(
                                        staff("Ravi", "Hair,Grooming", "FP-RAVI-WEBCITY", "22000",
                                                LocalDate.of(2024, 6, 1), true, "Aadhaar XXXX3312", "100000", "4"),
                                        staff("Sneha", "Skin,Nails", "FP-SNEHA-WEBCITY", "24000",
                                                LocalDate.of(2024, 1, 10), false, null, "110000", "4")
                                )),
                        branch("Mystic Alpine", "ALP", "Alpine Pyramid, Near Mall of Asia, Bangalore", "Mystic Alpine",
                                "29AABCU9603R1ZP", "9876543212", 13.0621, 77.5955, "320000",
                                "Alpine Manager", "manager.alpine@demo-brand.local",
                                List.of(
                                        staff("Kiran", "Hair,Grooming", "FP-KIRAN-ALPINE", "23000",
                                                LocalDate.of(2024, 7, 1), true, "Aadhaar XXXX6102", "105000", "4"),
                                        staff("Anjali", "Skin,Hair", "FP-ANJALI-ALPINE", "25000",
                                                LocalDate.of(2024, 4, 12), true, "PAN XXXX4411", "115000", "4")
                                )),
                        branch("Mystic GP", "GP", "Golden Palms, Near Manyata Tech Park, Bangalore", "Mystic GP",
                                "29AABCU9603R1ZQ", "9876543213", 13.0475, 77.6210, "300000",
                                "GP Manager", "manager.gp@demo-brand.local",
                                List.of(
                                        staff("Deepak", "Hair,Grooming", "FP-DEEPAK-GP", "22000",
                                                LocalDate.of(2024, 8, 1), true, "Aadhaar XXXX8821", "100000", "4"),
                                        staff("Meera", "Skin,Nails", "FP-MEERA-GP", "24000",
                                                LocalDate.of(2024, 5, 20), false, null, "108000", "4")
                                )),
                        branch("Mystic Varthur", "VAR", "SLV Sunrise, Varthur, Bangalore", "Varthur",
                                "29AABCU9603R1ZR", "9876543214", 12.9569, 77.7448, "280000",
                                "Varthur Manager", "manager.varthur@demo-brand.local",
                                List.of(
                                        staff("Arun", "Hair,Grooming", "FP-ARUN-VARTHUR", "21000",
                                                LocalDate.of(2024, 9, 1), true, "Aadhaar XXXX2290", "95000", "4"),
                                        staff("Nisha", "Skin,Hair", "FP-NISHA-VARTHUR", "23000",
                                                LocalDate.of(2024, 2, 18), true, "PAN XXXX5566", "100000", "4")
                                ))
                ));
    }

    /** Premium society competitor — higher pricing, stronger footfall. */
    private static TenantSeed velvetScissors() {
        return new TenantSeed(
                "Velvet Scissors",
                "velvet-scissors",
                "#be185d",
                "Velvet Admin",
                "ceo@velvet-scissors.local",
                "ceo123",
                new BigDecimal("1.15"),
                1.25,
                SalonTier.PREMIUM,
                List.of(
                        branch("Indiranagar Hub", "IND", "100 Feet Rd, Indiranagar, Bangalore", "Indiranagar Hub",
                                "29VELVET9603R1ZA", "9888001001", 12.9784, 77.6408, "450000",
                                "Indiranagar Manager", "manager.indiranagar@velvet-scissors.local",
                                List.of(
                                        staff("Rohan", "Hair,Color", "FP-ROHAN-IND", "30000",
                                                LocalDate.of(2023, 5, 1), true, "Aadhaar XXXX1001", "140000", "6"),
                                        staff("Ishita", "Skin,Hair", "FP-ISHITA-IND", "29000",
                                                LocalDate.of(2023, 11, 10), true, "PAN XXXX1002", "135000", "6")
                                )),
                        branch("Koramangala Studio", "KOR", "5th Block, Koramangala, Bangalore", "Koramangala Studio",
                                "29VELVET9603R1ZB", "9888001002", 12.9352, 77.6245, "420000",
                                "Koramangala Manager", "manager.koramangala@velvet-scissors.local",
                                List.of(
                                        staff("Vikram", "Hair,Grooming", "FP-VIKRAM-KOR", "27000",
                                                LocalDate.of(2024, 2, 1), true, "Aadhaar XXXX1003", "125000", "5"),
                                        staff("Sara", "Skin,Nails", "FP-SARA-KOR", "26000",
                                                LocalDate.of(2024, 3, 15), false, null, "120000", "5")
                                )),
                        branch("Whitefield Lounge", "WFD", "ITPL Main Rd, Whitefield, Bangalore", "Whitefield Lounge",
                                "29VELVET9603R1ZC", "9888001003", 12.9698, 77.7500, "380000",
                                "Whitefield Manager", "manager.whitefield@velvet-scissors.local",
                                List.of(
                                        staff("Kabir", "Hair,Grooming", "FP-KABIR-WFD", "25000",
                                                LocalDate.of(2024, 6, 1), true, "Aadhaar XXXX1004", "115000", "5"),
                                        staff("Diya", "Skin,Hair", "FP-DIYA-WFD", "25500",
                                                LocalDate.of(2024, 1, 20), true, "PAN XXXX1005", "118000", "5")
                                ))
                ));
    }

    /** Value-focused competitor — lower pricing, leaner volumes. */
    private static TenantSeed bloomBeauty() {
        return new TenantSeed(
                "Bloom Beauty Co",
                "bloom-beauty",
                "#059669",
                "Bloom Admin",
                "ceo@bloom-beauty.local",
                "ceo123",
                new BigDecimal("0.90"),
                0.75,
                SalonTier.BUDGET,
                List.of(
                        branch("HSR Layout", "HSR", "27th Main, HSR Layout, Bangalore", "HSR Layout",
                                "29BLOOM9603R1ZA", "9888002001", 12.9116, 77.6473, "220000",
                                "HSR Manager", "manager.hsr@bloom-beauty.local",
                                List.of(
                                        staff("Nikhil", "Hair,Grooming", "FP-NIKHIL-HSR", "20000",
                                                LocalDate.of(2024, 4, 1), true, "Aadhaar XXXX2001", "90000", "3"),
                                        staff("Pooja", "Skin,Nails", "FP-POOJA-HSR", "21000",
                                                LocalDate.of(2024, 5, 12), false, null, "92000", "3")
                                )),
                        branch("Jayanagar Salon", "JAY", "4th Block, Jayanagar, Bangalore", "Jayanagar Salon",
                                "29BLOOM9603R1ZB", "9888002002", 12.9308, 77.5838, "200000",
                                "Jayanagar Manager", "manager.jayanagar@bloom-beauty.local",
                                List.of(
                                        staff("Suresh", "Hair,Grooming", "FP-SURESH-JAY", "19500",
                                                LocalDate.of(2024, 7, 1), true, "Aadhaar XXXX2003", "85000", "3"),
                                        staff("Kavitha", "Skin,Hair", "FP-KAVITHA-JAY", "20500",
                                                LocalDate.of(2024, 8, 5), true, "PAN XXXX2004", "88000", "3")
                                ))
                ));
    }

    /** Mid-market growing chain — balanced pricing and volume. */
    private static TenantSeed crownAndComb() {
        return new TenantSeed(
                "Crown & Comb",
                "crown-comb",
                "#b45309",
                "Crown Admin",
                "ceo@crown-comb.local",
                "ceo123",
                new BigDecimal("1.05"),
                0.95,
                SalonTier.MID_MARKET,
                List.of(
                        branch("Hebbal Clubhouse", "HEB", "Hebbal Outer Ring Rd, Bangalore", "Hebbal Clubhouse",
                                "29CROWN9603R1ZA", "9888003001", 13.0358, 77.5970, "310000",
                                "Hebbal Manager", "manager.hebbal@crown-comb.local",
                                List.of(
                                        staff("Manish", "Hair,Grooming", "FP-MANISH-HEB", "24000",
                                                LocalDate.of(2024, 3, 1), true, "Aadhaar XXXX3001", "110000", "4"),
                                        staff("Riya", "Skin,Hair", "FP-RIYA-HEB", "24500",
                                                LocalDate.of(2024, 4, 18), true, "PAN XXXX3002", "112000", "4")
                                )),
                        branch("Electronic City", "ELC", "Phase 1, Electronic City, Bangalore", "Electronic City",
                                "29CROWN9603R1ZB", "9888003002", 12.8399, 77.6770, "290000",
                                "E-City Manager", "manager.ecity@crown-comb.local",
                                List.of(
                                        staff("Ajay", "Hair,Grooming", "FP-AJAY-ELC", "23000",
                                                LocalDate.of(2024, 6, 1), true, "Aadhaar XXXX3003", "100000", "4"),
                                        staff("Swathi", "Skin,Nails", "FP-SWATHI-ELC", "23500",
                                                LocalDate.of(2024, 2, 22), false, null, "102000", "4")
                                )),
                        branch("Marathahalli Point", "MRT", "Outer Ring Rd, Marathahalli, Bangalore", "Marathahalli Point",
                                "29CROWN9603R1ZC", "9888003003", 12.9591, 77.6974, "300000",
                                "Marathahalli Manager", "manager.marathahalli@crown-comb.local",
                                List.of(
                                        staff("Farhan", "Hair,Color", "FP-FARHAN-MRT", "25000",
                                                LocalDate.of(2024, 5, 1), true, "Aadhaar XXXX3005", "108000", "4"),
                                        staff("Aisha", "Skin,Hair", "FP-AISHA-MRT", "24800",
                                                LocalDate.of(2024, 9, 10), true, "PAN XXXX3006", "106000", "4")
                                ))
                ));
    }

    /** Premium spa-forward competitor — strong retail & repeat visits. */
    private static TenantSeed silkAndShear() {
        return new TenantSeed(
                "Silk & Shear",
                "silk-shear",
                "#0d9488",
                "Silk Admin",
                "ceo@silk-shear.local",
                "ceo123",
                new BigDecimal("1.20"),
                1.10,
                SalonTier.PREMIUM,
                List.of(
                        branch("Bellandur Spa", "BEL", "Bellandur Main Rd, Bangalore", "Bellandur Spa",
                                "29SILK9603R1ZA", "9888004001", 12.9260, 77.6761, "420000",
                                "Bellandur Manager", "manager.bellandur@silk-shear.local",
                                List.of(
                                        staff("Tarun", "Hair,Skin", "FP-TARUN-BEL", "28000",
                                                LocalDate.of(2024, 1, 1), true, "Aadhaar XXXX4001", "130000", "6"),
                                        staff("Neha", "Skin,Spa", "FP-NEHA-BEL", "29000",
                                                LocalDate.of(2024, 2, 15), true, "PAN XXXX4002", "135000", "6")
                                )),
                        branch("Sarjapur Studio", "SAR", "Sarjapur Rd, Bangalore", "Sarjapur Studio",
                                "29SILK9603R1ZB", "9888004002", 12.9010, 77.6950, "390000",
                                "Sarjapur Manager", "manager.sarjapur@silk-shear.local",
                                List.of(
                                        staff("Gopal", "Hair,Grooming", "FP-GOPAL-SAR", "26000",
                                                LocalDate.of(2024, 4, 1), true, "Aadhaar XXXX4003", "120000", "5"),
                                        staff("Lakshmi", "Skin,Hair", "FP-LAKSHMI-SAR", "27000",
                                                LocalDate.of(2024, 5, 20), true, "PAN XXXX4004", "125000", "5")
                                ))
                ));
    }

    private static BranchSeed branch(
            String name, String code, String address, String societyDefault,
            String gstin, String phone, double lat, double lng, String target,
            String managerName, String managerEmail, List<StaffSeed> staff) {
        return new BranchSeed(name, code, address, societyDefault, gstin, phone, lat, lng, target,
                managerName, managerEmail, staff);
    }

    private static StaffSeed staff(
            String name, String skills, String biometricId, String salary,
            LocalDate joiningDate, boolean idProofCollected, String idProofReference,
            String monthlySalesTarget, String incentivePercent) {
        return new StaffSeed(name, skills, biometricId, salary, joiningDate, idProofCollected,
                idProofReference, monthlySalesTarget, incentivePercent);
    }
}
