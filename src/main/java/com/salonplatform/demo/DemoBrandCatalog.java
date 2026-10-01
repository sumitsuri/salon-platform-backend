package com.salonplatform.demo;

import com.salonplatform.domain.enums.BranchBusinessType;
import com.salonplatform.domain.enums.InventoryUnit;
import com.salonplatform.domain.enums.MembershipCadence;
import com.salonplatform.domain.enums.ProductCategory;

import java.time.DayOfWeek;
import java.util.List;

/**
 * The fictional demo chain: five Bangalore branches, each carrying one story the videos rely on.
 * Everything here is invented — no row is copied from a real brand.
 */
final class DemoBrandCatalog {

    private DemoBrandCatalog() {}

    /** Story knobs: volume ramps, churn pressure, rating dips and wastage spikes the demos call out. */
    enum Story { FLAGSHIP, GROWTH, RETENTION_DIP, COST_SPIKE, MEMBERSHIP_HEAVY }

    record BranchDef(String code, String name, String address, String locality, double lat, double lng,
                     String gstin, String phone, long monthlyTarget, long rent, long accommodation,
                     long utilities, double volumeFactor, BranchBusinessType businessType,
                     double googleRating, int googleReviewCount, int searchRank,
                     String managerName, String managerLocalPart, Story story, List<String> societies) {}

    record StaffDef(String branchCode, String name, String skills, long salary, int joinedDaysAgo,
                    long monthlyTarget, int incentivePercent, double demandWeight, boolean lateProne,
                    DayOfWeek weeklyOff, String avatar) {}

    /** gender: F, M or U (unisex). usage: "SKU:qty;SKU:qty" consumed per service. */
    record ServiceDef(String category, String name, int price, int minutes, String gender, double weight,
                      String skill, String usage) {}

    record ProductDef(String sku, String name, ProductCategory category, InventoryUnit unit, double unitCost,
                      Integer retailPrice, int reorderLevel, int vendor) {}

    record MembershipDef(String name, String description, MembershipCadence cadence, int fee, int benefitPercent) {}

    /** items: "Service name x qty" pairs. */
    record PackageDef(String name, String description, int packagePrice, int validityDays, List<String> items) {}

    record CompetitorDef(String branchCode, String name, boolean aspirational, String address, int revenuePerDay,
                         int avgTicket, double rating, int reviews, int searchRank, double repeatRate) {}

    enum CampaignKind { FESTIVE, WINBACK, MEMBERSHIP_RENEWAL, BRANCH_WINBACK }

    record CampaignDef(int dayOffset, CampaignKind kind, String name, String message, String branchCode) {}

    static final List<BranchDef> BRANCHES = List.of(
            new BranchDef("KOR", "Koramangala", "No. 14, 80 Feet Road, 4th Block, Koramangala, Bengaluru 560034",
                    "Koramangala", 12.9352, 77.6245, "29AAKCA4821D1Z1", "08041120101", 1_150_000, 285_000, 42_000,
                    48_000, 1.25, BranchBusinessType.SALON_AND_SPA, 4.6, 1284, 2, "Sneha Kulkarni", "kor",
                    Story.FLAGSHIP, List.of("Koramangala 4th Block", "Koramangala 5th Block", "ST Bed Layout", "Ejipura")),
            new BranchDef("IND", "Indiranagar", "No. 22, 12th Main Road, HAL 2nd Stage, Indiranagar, Bengaluru 560038",
                    "Indiranagar", 12.9719, 77.6412, "29AAKCA4821D1Z2", "08041120102", 1_000_000, 255_000, 38_000,
                    44_000, 1.05, BranchBusinessType.SALON_AND_SPA, 4.5, 968, 3, "Rohan Mehta", "ind",
                    Story.GROWTH, List.of("HAL 2nd Stage", "Defence Colony", "Domlur", "Jeevan Bima Nagar")),
            new BranchDef("WHF", "Whitefield", "Unit 3, Ground Floor, ITPL Main Road, Whitefield, Bengaluru 560066",
                    "Whitefield", 12.9698, 77.7500, "29AAKCA4821D1Z3", "08041120103", 950_000, 215_000, 36_000,
                    42_000, 1.0, BranchBusinessType.SALON, 4.2, 742, 5, "Arvind Pillai", "whf",
                    Story.RETENTION_DIP, List.of("Brookefield", "Hope Farm", "ITPL", "Kundalahalli")),
            new BranchDef("HSR", "HSR Layout", "No. 1187, 27th Main, Sector 2, HSR Layout, Bengaluru 560102",
                    "HSR Layout", 12.9116, 77.6474, "29AAKCA4821D1Z4", "08041120104", 900_000, 200_000, 34_000,
                    40_000, 0.95, BranchBusinessType.SALON, 4.4, 655, 4, "Divya Raman", "hsr",
                    Story.COST_SPIKE, List.of("Sector 1", "Sector 2", "Sector 6", "Agara")),
            new BranchDef("JAY", "Jayanagar", "No. 41, 11th Main Road, 4th Block, Jayanagar, Bengaluru 560011",
                    "Jayanagar", 12.9250, 77.5938, "29AAKCA4821D1Z5", "08041120105", 880_000, 185_000, 30_000,
                    38_000, 0.9, BranchBusinessType.SALON_AND_SPA, 4.7, 1102, 1, "Prakash Rao", "jay",
                    Story.MEMBERSHIP_HEAVY, List.of("4th Block", "9th Block", "JP Nagar 1st Phase", "Basavanagudi"))
    );

    static final List<StaffDef> STAFF = List.of(
            // Koramangala — Riya is the star stylist the videos keep coming back to.
            new StaffDef("KOR", "Riya Sharma", "Hair,Colour", 52_000, 900, 260_000, 8, 2.6, false, DayOfWeek.TUESDAY, "riya-sharma"),
            new StaffDef("KOR", "Arjun Nair", "Hair,Grooming", 34_000, 700, 170_000, 6, 1.0, false, DayOfWeek.WEDNESDAY, "arjun-nair"),
            new StaffDef("KOR", "Fatima Sheikh", "Skin,Makeup", 36_000, 820, 180_000, 6, 1.1, false, DayOfWeek.THURSDAY, "fatima-sheikh"),
            new StaffDef("KOR", "Kavya Reddy", "Nails,Skin", 28_000, 400, 140_000, 5, 1.0, false, DayOfWeek.MONDAY, "kavya-reddy"),
            new StaffDef("KOR", "Rahul Das", "Hair,Grooming", 30_000, 520, 150_000, 5, 1.0, true, DayOfWeek.FRIDAY, "rahul-das"),
            new StaffDef("KOR", "Lalitha Gowda", "Spa", 32_000, 640, 160_000, 6, 1.0, false, DayOfWeek.TUESDAY, "lalitha-gowda"),
            new StaffDef("KOR", "Imran Khan", "Hair,Colour", 38_000, 1100, 190_000, 6, 1.2, false, DayOfWeek.MONDAY, "imran-khan"),
            // Indiranagar — growth branch; Neha and Karthik carry the recent uplift.
            new StaffDef("IND", "Neha Kapoor", "Hair,Colour", 42_000, 760, 210_000, 7, 1.5, false, DayOfWeek.TUESDAY, "neha-kapoor"),
            new StaffDef("IND", "Vikram Rao", "Hair,Grooming", 30_000, 600, 150_000, 5, 1.0, false, DayOfWeek.WEDNESDAY, "vikram-rao"),
            new StaffDef("IND", "Sana Mirza", "Skin,Makeup", 34_000, 450, 170_000, 6, 1.1, false, DayOfWeek.THURSDAY, "sana-mirza"),
            new StaffDef("IND", "Pooja Iyer", "Nails,Skin", 26_000, 300, 130_000, 5, 1.0, false, DayOfWeek.MONDAY, "pooja-iyer"),
            new StaffDef("IND", "Karthik Menon", "Hair,Grooming", 33_000, 120, 160_000, 6, 1.3, false, DayOfWeek.FRIDAY, "karthik-menon"),
            new StaffDef("IND", "Divya Hegde", "Spa,Skin", 31_000, 690, 150_000, 6, 1.0, false, DayOfWeek.TUESDAY, "divya-hegde"),
            new StaffDef("IND", "Aman Verma", "Hair,Grooming", 27_000, 380, 130_000, 5, 0.9, true, DayOfWeek.MONDAY, "aman-verma"),
            // Whitefield — retention dip; two late-prone staff feed the attendance exceptions story.
            new StaffDef("WHF", "Tanvi Joshi", "Hair,Colour", 36_000, 820, 180_000, 6, 1.2, false, DayOfWeek.TUESDAY, "tanvi-joshi"),
            new StaffDef("WHF", "Rohit Kulkarni", "Hair,Grooming", 29_000, 540, 140_000, 5, 1.0, true, DayOfWeek.WEDNESDAY, "rohit-kulkarni"),
            new StaffDef("WHF", "Ayesha Siddiqui", "Skin,Makeup", 33_000, 610, 160_000, 6, 1.1, false, DayOfWeek.THURSDAY, "ayesha-siddiqui"),
            new StaffDef("WHF", "Meghna Pillai", "Nails,Skin", 25_000, 260, 120_000, 5, 1.0, false, DayOfWeek.MONDAY, "meghna-pillai"),
            new StaffDef("WHF", "Suresh Babu", "Hair,Colour", 31_000, 980, 150_000, 5, 1.0, true, DayOfWeek.FRIDAY, "suresh-babu"),
            new StaffDef("WHF", "Nandini Rao", "Spa", 29_000, 450, 140_000, 5, 1.0, false, DayOfWeek.TUESDAY, "nandini-rao"),
            new StaffDef("WHF", "Joseph Thomas", "Hair,Grooming", 26_000, 90, 120_000, 5, 0.9, false, DayOfWeek.MONDAY, "joseph-thomas"),
            // HSR — cost spike; Ishita runs the colour/keratin chair that drives product use.
            new StaffDef("HSR", "Ishita Bose", "Hair,Colour", 37_000, 700, 180_000, 6, 1.4, false, DayOfWeek.TUESDAY, "ishita-bose"),
            new StaffDef("HSR", "Manoj Kumar", "Hair,Grooming", 28_000, 560, 140_000, 5, 1.0, false, DayOfWeek.WEDNESDAY, "manoj-kumar"),
            new StaffDef("HSR", "Shruti Patil", "Skin,Nails", 30_000, 480, 150_000, 6, 1.1, false, DayOfWeek.THURSDAY, "shruti-patil"),
            new StaffDef("HSR", "Priyanka Das", "Skin,Makeup", 29_000, 330, 140_000, 5, 1.0, false, DayOfWeek.MONDAY, "priyanka-das"),
            new StaffDef("HSR", "Farhan Ali", "Hair,Grooming", 27_000, 410, 130_000, 5, 1.0, false, DayOfWeek.FRIDAY, "farhan-ali"),
            new StaffDef("HSR", "Lakshmi Narayan", "Spa", 28_000, 760, 130_000, 5, 1.0, false, DayOfWeek.TUESDAY, "lakshmi-narayan"),
            new StaffDef("HSR", "Deepa Shetty", "Hair,Colour", 30_000, 200, 140_000, 5, 1.0, false, DayOfWeek.MONDAY, "deepa-shetty"),
            // Jayanagar — older, membership-heavy clientele.
            new StaffDef("JAY", "Anitha Murthy", "Skin,Makeup", 35_000, 1200, 170_000, 6, 1.3, false, DayOfWeek.TUESDAY, "anitha-murthy"),
            new StaffDef("JAY", "Ravi Shankar", "Hair,Grooming", 30_000, 1000, 150_000, 5, 1.1, false, DayOfWeek.WEDNESDAY, "ravi-shankar"),
            new StaffDef("JAY", "Swathi Rao", "Hair,Colour", 34_000, 880, 170_000, 6, 1.2, false, DayOfWeek.THURSDAY, "swathi-rao"),
            new StaffDef("JAY", "Bhavana Gowda", "Nails,Skin", 26_000, 520, 130_000, 5, 1.0, false, DayOfWeek.MONDAY, "bhavana-gowda"),
            new StaffDef("JAY", "Prakash Hegde", "Hair,Grooming", 28_000, 760, 140_000, 5, 1.0, false, DayOfWeek.FRIDAY, "prakash-hegde"),
            new StaffDef("JAY", "Geetha Krishnan", "Spa", 30_000, 940, 140_000, 5, 1.0, false, DayOfWeek.TUESDAY, "geetha-krishnan"),
            new StaffDef("JAY", "Naveen Raj", "Hair,Grooming", 25_000, 150, 120_000, 5, 0.9, false, DayOfWeek.MONDAY, "naveen-raj")
    );

    static final List<String> CATEGORIES = List.of(
            "Hair Cut & Style", "Hair Colour", "Hair Treatments", "Skin & Facials", "Men's Grooming",
            "Waxing & Threading", "Nails", "Spa & Massage", "Bridal & Makeup");

    static final List<ServiceDef> SERVICES = List.of(
            new ServiceDef("Hair Cut & Style", "Women's Haircut", 900, 45, "F", 10, "Hair", "SHMP:35;COND:20;TOWL:2;NECK:1"),
            new ServiceDef("Hair Cut & Style", "Senior Stylist Haircut (Women)", 1400, 60, "F", 4, "Hair", "SHMP:35;COND:20;TOWL:2;NECK:1"),
            new ServiceDef("Hair Cut & Style", "Men's Haircut", 450, 30, "M", 14, "Hair", "SHMP:20;TOWL:1;NECK:1"),
            new ServiceDef("Hair Cut & Style", "Senior Stylist Haircut (Men)", 700, 40, "M", 4, "Hair", "SHMP:20;TOWL:1;NECK:1"),
            new ServiceDef("Hair Cut & Style", "Kids Haircut", 350, 25, "U", 2, "Hair", "SHMP:15;TOWL:1;NECK:1"),
            new ServiceDef("Hair Cut & Style", "Haircut + Wash (Women)", 1100, 50, "F", 4, "Hair", "SHMP:35;COND:20;TOWL:2;NECK:1"),
            new ServiceDef("Hair Cut & Style", "Blow Dry", 600, 30, "F", 5, "Hair", "SHMP:30;HPRT:5;TOWL:1"),
            new ServiceDef("Hair Cut & Style", "Hair Wash & Blast Dry", 400, 20, "U", 3, "Hair", "SHMP:30;COND:15;TOWL:1"),
            new ServiceDef("Hair Cut & Style", "Ironing", 800, 40, "F", 2.5, "Hair", "HPRT:8;SERM:2"),
            new ServiceDef("Hair Cut & Style", "Tonging / Curls", 900, 45, "F", 2, "Hair", "HPRT:8;SERM:2"),
            new ServiceDef("Hair Cut & Style", "Party Updo", 1800, 60, "F", 1, "Hair", "HPRT:10;SERM:3"),
            new ServiceDef("Hair Cut & Style", "Fringe Trim", 250, 15, "F", 1.5, "Hair", "NECK:1"),
            new ServiceDef("Hair Colour", "Global Colour (Short)", 2800, 90, "F", 2.5, "Colour", "CLRT:2;DEV6:90;GLOV:2;TOWL:2"),
            new ServiceDef("Hair Colour", "Global Colour (Long)", 4200, 120, "F", 2, "Colour", "CLRT:3;DEV6:140;GLOV:2;TOWL:2"),
            new ServiceDef("Hair Colour", "Root Touch-up", 1500, 60, "F", 4, "Colour", "CLRT:1;DEV6:60;GLOV:2;TOWL:1"),
            new ServiceDef("Hair Colour", "Ammonia-free Root Touch-up", 1900, 60, "F", 2, "Colour", "CLRT:1;DEV6:60;GLOV:2;TOWL:1"),
            new ServiceDef("Hair Colour", "Highlights (Partial)", 3500, 120, "F", 1.5, "Colour", "BLCH:40;CLRT:1;DEV6:80;GLOV:2"),
            new ServiceDef("Hair Colour", "Highlights (Full)", 5500, 150, "F", 0.8, "Colour", "BLCH:80;CLRT:2;DEV6:140;GLOV:2"),
            new ServiceDef("Hair Colour", "Balayage", 6500, 180, "F", 0.8, "Colour", "BLCH:90;CLRT:2;DEV6:150;BOND:15;GLOV:2"),
            new ServiceDef("Hair Colour", "Fashion Shade Streaks", 2500, 90, "U", 0.8, "Colour", "BLCH:30;CLRT:1;DEV6:60;GLOV:2"),
            new ServiceDef("Hair Colour", "Men's Hair Colour", 900, 45, "M", 3, "Colour", "CLRT:1;DEV6:40;GLOV:2"),
            new ServiceDef("Hair Colour", "Beard Colour", 450, 20, "M", 2, "Colour", "CLRT:0.3;DEV6:15;GLOV:2"),
            new ServiceDef("Hair Treatments", "Keratin Treatment", 6500, 150, "F", 1.4, "Colour", "KERK:1;SHMP:40;SERM:3;GLOV:2"),
            new ServiceDef("Hair Treatments", "Smoothening", 5500, 150, "F", 1.0, "Colour", "SMTK:1;SHMP:40;SERM:3;GLOV:2"),
            new ServiceDef("Hair Treatments", "Hair Botox", 7000, 150, "F", 0.7, "Colour", "BTXK:1;SHMP:40;SERM:3;GLOV:2"),
            new ServiceDef("Hair Treatments", "Nanoplastia", 7500, 180, "F", 0.5, "Colour", "KERK:1;SHMP:40;SERM:4;GLOV:2"),
            new ServiceDef("Hair Treatments", "Hair Spa (Classic)", 1500, 45, "U", 5, "Hair", "SPAC:60;SHMP:30;TOWL:2"),
            new ServiceDef("Hair Treatments", "Hair Spa (Moroccan Oil)", 2200, 60, "F", 2.5, "Hair", "SPAC:60;SERM:5;SHMP:30;TOWL:2"),
            new ServiceDef("Hair Treatments", "Bond Repair Treatment", 3000, 60, "F", 1, "Colour", "BOND:20;SHMP:30;TOWL:2"),
            new ServiceDef("Hair Treatments", "Anti-dandruff Treatment", 1800, 45, "U", 1.5, "Hair", "SPAC:40;SHMP:30;TOWL:1"),
            new ServiceDef("Hair Treatments", "Scalp Detox", 2000, 45, "U", 1, "Hair", "SPAC:40;SHMP:30;TOWL:1"),
            new ServiceDef("Skin & Facials", "Classic Cleanup", 900, 40, "U", 4, "Skin", "CLNK:1;TOWL:2"),
            new ServiceDef("Skin & Facials", "Fruit Facial", 1500, 60, "F", 3, "Skin", "FRTK:1;TOWL:2"),
            new ServiceDef("Skin & Facials", "O3+ Whitening Facial", 3200, 75, "F", 1.5, "Skin", "O3KT:1;TOWL:2"),
            new ServiceDef("Skin & Facials", "Hydra Facial", 4500, 75, "U", 1.2, "Skin", "HYDK:1;TOWL:2"),
            new ServiceDef("Skin & Facials", "Anti-ageing Facial", 3800, 75, "F", 0.8, "Skin", "HYDK:1;TOWL:2"),
            new ServiceDef("Skin & Facials", "Gold Facial", 2500, 60, "F", 1.5, "Skin", "GLDK:1;TOWL:2"),
            new ServiceDef("Skin & Facials", "Charcoal Detox Facial", 1800, 60, "M", 1.2, "Skin", "FRTK:1;TOWL:2"),
            new ServiceDef("Skin & Facials", "De-tan Pack (Face)", 700, 30, "U", 3, "Skin", "DTAN:40;TOWL:1"),
            new ServiceDef("Skin & Facials", "Bleach (Face)", 500, 20, "F", 2, "Skin", "BLCR:20;TOWL:1"),
            new ServiceDef("Skin & Facials", "Men's Express Facial", 1200, 45, "M", 2, "Skin", "CLNK:1;TOWL:2"),
            new ServiceDef("Men's Grooming", "Beard Trim", 250, 15, "M", 9, "Grooming", "TOWL:1"),
            new ServiceDef("Men's Grooming", "Beard Styling", 400, 25, "M", 4, "Grooming", "SHVC:5;TOWL:1"),
            new ServiceDef("Men's Grooming", "Clean Shave", 300, 20, "M", 3, "Grooming", "SHVC:10;RAZR:1;TOWL:1"),
            new ServiceDef("Men's Grooming", "Head Massage", 500, 20, "U", 4, "Grooming", "MOIL:15"),
            new ServiceDef("Men's Grooming", "Men's Grooming Combo", 1100, 60, "M", 3, "Grooming", "SHMP:20;SHVC:10;RAZR:1;TOWL:2"),
            new ServiceDef("Waxing & Threading", "Eyebrow Threading", 80, 10, "F", 14, "Skin", "THRD:0.05"),
            new ServiceDef("Waxing & Threading", "Upper Lip Threading", 50, 5, "F", 9, "Skin", "THRD:0.03"),
            new ServiceDef("Waxing & Threading", "Full Face Threading", 300, 20, "F", 2, "Skin", "THRD:0.1"),
            new ServiceDef("Waxing & Threading", "Half Arms Wax", 400, 20, "F", 2, "Skin", "RWAX:40;STRP:8"),
            new ServiceDef("Waxing & Threading", "Full Arms Wax", 700, 30, "F", 4, "Skin", "RWAX:70;STRP:14"),
            new ServiceDef("Waxing & Threading", "Half Legs Wax", 600, 30, "F", 2, "Skin", "RWAX:60;STRP:12"),
            new ServiceDef("Waxing & Threading", "Full Legs Wax", 1000, 45, "F", 3.5, "Skin", "RWAX:110;STRP:22"),
            new ServiceDef("Waxing & Threading", "Underarms Wax", 250, 10, "F", 4, "Skin", "RWAX:15;STRP:3"),
            new ServiceDef("Waxing & Threading", "Full Body Wax", 3200, 120, "F", 0.6, "Skin", "RWAX:300;STRP:60"),
            new ServiceDef("Waxing & Threading", "Bikini Wax", 1200, 30, "F", 0.8, "Skin", "RWAX:40;STRP:8"),
            new ServiceDef("Nails", "Classic Manicure", 700, 40, "U", 4, "Nails", "MANK:1;TOWL:1"),
            new ServiceDef("Nails", "Classic Pedicure", 900, 50, "U", 4, "Nails", "PEDK:1;TOWL:1"),
            new ServiceDef("Nails", "Spa Pedicure", 1500, 60, "F", 2, "Nails", "PEDK:1;BPOL:30;TOWL:2"),
            new ServiceDef("Nails", "Gel Polish", 1200, 45, "F", 2.5, "Nails", "GELP:0.06"),
            new ServiceDef("Nails", "Gel Extensions", 2500, 90, "F", 1, "Nails", "TIPS:0.1;GELP:0.08"),
            new ServiceDef("Nails", "Nail Art (per set)", 800, 30, "F", 1.2, "Nails", "GELP:0.04"),
            new ServiceDef("Nails", "Gel Removal", 400, 20, "F", 1, "Nails", "TOWL:1"),
            new ServiceDef("Spa & Massage", "Swedish Massage (60 min)", 2800, 60, "U", 1.6, "Spa", "MOIL:40;TOWL:3"),
            new ServiceDef("Spa & Massage", "Deep Tissue Massage (60 min)", 3200, 60, "U", 1.3, "Spa", "MOIL:40;TOWL:3"),
            new ServiceDef("Spa & Massage", "Balinese Massage (60 min)", 3000, 60, "U", 0.9, "Spa", "MOIL:40;TOWL:3"),
            new ServiceDef("Spa & Massage", "Aromatherapy (90 min)", 4200, 90, "U", 0.6, "Spa", "MOIL:60;TOWL:3"),
            new ServiceDef("Spa & Massage", "Foot Reflexology", 1200, 30, "U", 2, "Spa", "MOIL:15;TOWL:1"),
            new ServiceDef("Spa & Massage", "Head, Neck & Shoulder", 1000, 30, "U", 2, "Spa", "MOIL:15;TOWL:1"),
            new ServiceDef("Spa & Massage", "Body Polish", 3500, 60, "F", 0.6, "Spa", "BPOL:150;TOWL:3"),
            new ServiceDef("Spa & Massage", "Couple Massage (60 min)", 5600, 60, "U", 0.3, "Spa", "MOIL:80;TOWL:6"),
            new ServiceDef("Bridal & Makeup", "Party Makeup", 3500, 60, "F", 1, "Makeup", "MKUP:0.04"),
            new ServiceDef("Bridal & Makeup", "HD Party Makeup", 5000, 75, "F", 0.6, "Makeup", "MKUP:0.05"),
            new ServiceDef("Bridal & Makeup", "Engagement Makeup", 9000, 120, "F", 0.25, "Makeup", "MKUP:0.08"),
            new ServiceDef("Bridal & Makeup", "Bridal Makeup (HD)", 22000, 180, "F", 0.12, "Makeup", "MKUP:0.12"),
            new ServiceDef("Bridal & Makeup", "Pre-bridal Package", 15000, 240, "F", 0.15, "Skin", "HYDK:1;RWAX:300;STRP:60;MOIL:40"),
            new ServiceDef("Bridal & Makeup", "Saree Draping", 800, 30, "F", 0.6, "Makeup", "")
    );

    static final List<String> VENDORS = List.of(
            "Southern Salon Supplies", "ProCare Distributors", "Bloom Beauty Wholesale",
            "Spa Essentials India", "NailCraft Traders", "Linen & Disposables Co.");

    static final List<ProductDef> PRODUCTS = List.of(
            new ProductDef("SHMP", "Sulphate-free Shampoo (5 L can)", ProductCategory.CONSUMABLE, InventoryUnit.ML, 0.9, null, 4000, 0),
            new ProductDef("COND", "Conditioning Rinse (5 L can)", ProductCategory.CONSUMABLE, InventoryUnit.ML, 1.0, null, 2500, 0),
            new ProductDef("CLRT", "Hair Colour Tube 60 g", ProductCategory.CONSUMABLE, InventoryUnit.PCS, 380, null, 40, 1),
            new ProductDef("DEV6", "Developer 6% (1 L)", ProductCategory.CONSUMABLE, InventoryUnit.ML, 0.35, null, 3000, 1),
            new ProductDef("BLCH", "Lightening Powder", ProductCategory.CONSUMABLE, InventoryUnit.G, 1.6, null, 1000, 1),
            new ProductDef("KERK", "Keratin Treatment Kit", ProductCategory.CONSUMABLE, InventoryUnit.PCS, 1800, null, 6, 1),
            new ProductDef("SMTK", "Smoothening Cream Kit", ProductCategory.CONSUMABLE, InventoryUnit.PCS, 1500, null, 5, 1),
            new ProductDef("BTXK", "Hair Botox Kit", ProductCategory.CONSUMABLE, InventoryUnit.PCS, 2100, null, 3, 1),
            new ProductDef("BOND", "Bond Builder Treatment", ProductCategory.CONSUMABLE, InventoryUnit.ML, 12, null, 250, 1),
            new ProductDef("SPAC", "Hair Spa Cream", ProductCategory.CONSUMABLE, InventoryUnit.G, 1.2, null, 2500, 0),
            new ProductDef("SERM", "Argan Oil Serum", ProductCategory.CONSUMABLE, InventoryUnit.ML, 4, null, 300, 0),
            new ProductDef("HPRT", "Heat Protection Spray", ProductCategory.CONSUMABLE, InventoryUnit.ML, 1.5, null, 500, 0),
            new ProductDef("FRTK", "Fruit Facial Kit", ProductCategory.CONSUMABLE, InventoryUnit.PCS, 220, null, 20, 2),
            new ProductDef("GLDK", "Gold Facial Kit", ProductCategory.CONSUMABLE, InventoryUnit.PCS, 420, null, 12, 2),
            new ProductDef("HYDK", "Hydra Facial Serum Set", ProductCategory.CONSUMABLE, InventoryUnit.PCS, 950, null, 10, 2),
            new ProductDef("O3KT", "Whitening Facial Kit", ProductCategory.CONSUMABLE, InventoryUnit.PCS, 780, null, 8, 2),
            new ProductDef("CLNK", "Cleanup Kit", ProductCategory.CONSUMABLE, InventoryUnit.PCS, 140, null, 30, 2),
            new ProductDef("DTAN", "De-tan Pack", ProductCategory.CONSUMABLE, InventoryUnit.G, 2.2, null, 1000, 2),
            new ProductDef("BLCR", "Bleach Cream", ProductCategory.CONSUMABLE, InventoryUnit.G, 1.8, null, 500, 2),
            new ProductDef("RWAX", "Liquid Rica Wax", ProductCategory.CONSUMABLE, InventoryUnit.G, 1.4, null, 3000, 2),
            new ProductDef("STRP", "Wax Strips", ProductCategory.CONSUMABLE, InventoryUnit.PCS, 2, null, 600, 5),
            new ProductDef("THRD", "Threading Spool", ProductCategory.CONSUMABLE, InventoryUnit.PCS, 25, null, 10, 5),
            new ProductDef("MANK", "Disposable Manicure Kit", ProductCategory.CONSUMABLE, InventoryUnit.PCS, 90, null, 40, 4),
            new ProductDef("PEDK", "Disposable Pedicure Kit", ProductCategory.CONSUMABLE, InventoryUnit.PCS, 120, null, 40, 4),
            new ProductDef("GELP", "Gel Polish Bottle", ProductCategory.CONSUMABLE, InventoryUnit.PCS, 450, null, 6, 4),
            new ProductDef("TIPS", "Nail Extension Tips (box)", ProductCategory.CONSUMABLE, InventoryUnit.PCS, 600, null, 3, 4),
            new ProductDef("MOIL", "Aroma Massage Oil", ProductCategory.CONSUMABLE, InventoryUnit.ML, 0.8, null, 3000, 3),
            new ProductDef("BPOL", "Body Polish Scrub", ProductCategory.CONSUMABLE, InventoryUnit.G, 2.5, null, 1000, 3),
            new ProductDef("SHVC", "Shaving Cream", ProductCategory.CONSUMABLE, InventoryUnit.ML, 0.5, null, 1000, 0),
            new ProductDef("RAZR", "Disposable Razor", ProductCategory.CONSUMABLE, InventoryUnit.PCS, 12, null, 100, 5),
            new ProductDef("MKUP", "HD Makeup Base Set", ProductCategory.CONSUMABLE, InventoryUnit.PCS, 2500, null, 1, 2),
            new ProductDef("TOWL", "Disposable Towel", ProductCategory.CONSUMABLE, InventoryUnit.PCS, 6, null, 800, 5),
            new ProductDef("GLOV", "Nitrile Gloves", ProductCategory.CONSUMABLE, InventoryUnit.PCS, 4, null, 600, 5),
            new ProductDef("NECK", "Neck Strips", ProductCategory.CONSUMABLE, InventoryUnit.PCS, 1.5, null, 1500, 5),
            new ProductDef("RSHM", "Repair Shampoo 300 ml (retail)", ProductCategory.RETAIL, InventoryUnit.BOTTLE, 420, 850, 15, 0),
            new ProductDef("RCND", "Repair Conditioner 300 ml (retail)", ProductCategory.RETAIL, InventoryUnit.BOTTLE, 450, 900, 12, 0),
            new ProductDef("RSRM", "Frizz Control Serum 100 ml (retail)", ProductCategory.RETAIL, InventoryUnit.BOTTLE, 520, 1150, 10, 0),
            new ProductDef("RFWS", "Gentle Face Wash (retail)", ProductCategory.RETAIL, InventoryUnit.PCS, 260, 549, 15, 2),
            new ProductDef("RSUN", "Sunscreen SPF 50 (retail)", ProductCategory.RETAIL, InventoryUnit.PCS, 380, 799, 12, 2),
            new ProductDef("RMSK", "Hair Mask 200 g (retail)", ProductCategory.RETAIL, InventoryUnit.PCS, 600, 1299, 8, 0)
    );

    static final List<MembershipDef> MEMBERSHIPS = List.of(
            new MembershipDef("Aura Silver", "10% off every service for 6 months", MembershipCadence.MONTHS_6, 1999, 10),
            new MembershipDef("Aura Gold", "15% off every service for 12 months", MembershipCadence.MONTHS_12, 3999, 15),
            new MembershipDef("Aura Platinum", "20% off every service for 12 months", MembershipCadence.MONTHS_12, 7999, 20)
    );

    static final List<PackageDef> PACKAGES = List.of(
            new PackageDef("Hair Spa x4", "Four classic hair spas", 4800, 120, List.of("Hair Spa (Classic) x4")),
            new PackageDef("Facial Glow x3", "Three fruit facials", 3600, 120, List.of("Fruit Facial x3")),
            new PackageDef("Groom Pack x6", "Six men's haircuts", 2200, 180, List.of("Men's Haircut x6")),
            new PackageDef("Mani-Pedi x4", "Four manicures and four pedicures", 5200, 180,
                    List.of("Classic Manicure x4", "Classic Pedicure x4")),
            new PackageDef("Keratin Care", "Keratin treatment with three Moroccan hair spas", 10500, 180,
                    List.of("Keratin Treatment x1", "Hair Spa (Moroccan Oil) x3"))
    );

    static final List<CompetitorDef> COMPETITORS = List.of(
            new CompetitorDef("KOR", "Mirror & Mane Studio", false, "1st Cross, Koramangala 5th Block", 31000, 1350, 4.4, 860, 3, 0.42),
            new CompetitorDef("KOR", "The Velvet Chair", true, "80 Feet Road, Koramangala", 52000, 1900, 4.7, 2140, 1, 0.51),
            new CompetitorDef("KOR", "Snip & Style Unisex", false, "Ejipura Main Road", 14000, 650, 4.0, 310, 7, 0.33),
            new CompetitorDef("IND", "Indigo Hair Lounge", false, "100 Feet Road, Indiranagar", 29000, 1450, 4.5, 1020, 2, 0.45),
            new CompetitorDef("IND", "Glow Theory Skin & Hair", true, "12th Main, Indiranagar", 47000, 2100, 4.6, 1650, 1, 0.49),
            new CompetitorDef("IND", "Cut Above Express", false, "Domlur Layout", 12000, 600, 3.9, 280, 8, 0.30),
            new CompetitorDef("WHF", "Brookfield Beauty Bar", false, "Brookefield Main Road", 26000, 1200, 4.5, 740, 2, 0.46),
            new CompetitorDef("WHF", "Polished Studio", true, "ITPL Main Road", 39000, 1700, 4.6, 1310, 1, 0.50),
            new CompetitorDef("WHF", "Quick Trim Point", false, "Kundalahalli Gate", 11000, 520, 3.8, 190, 9, 0.28),
            new CompetitorDef("HSR", "Sector Seven Salon", false, "27th Main, HSR Layout", 24000, 1150, 4.3, 610, 3, 0.43),
            new CompetitorDef("HSR", "Lumiere Hair & Skin", true, "HSR BDA Complex", 41000, 1800, 4.6, 1180, 1, 0.48),
            new CompetitorDef("HSR", "Budget Barbers HSR", false, "Agara Lake Road", 9000, 380, 3.9, 250, 10, 0.31),
            new CompetitorDef("JAY", "Heritage Beauty Parlour", false, "9th Block, Jayanagar", 18000, 900, 4.4, 980, 4, 0.55),
            new CompetitorDef("JAY", "Silk Route Spa", true, "4th Block, Jayanagar", 36000, 2200, 4.5, 870, 2, 0.47),
            new CompetitorDef("JAY", "Neat Cuts Jayanagar", false, "11th Main, Jayanagar", 10000, 450, 4.0, 340, 7, 0.36)
    );

    /** Offsets are days from the history start; win-backs land late enough for their results to show. */
    static final List<CampaignDef> CAMPAIGNS = List.of(
            new CampaignDef(22, CampaignKind.FESTIVE, "Festive Glow Week",
                    "Festive looks are on us — 15% off facials and styling this week.", null),
            new CampaignDef(58, CampaignKind.WINBACK, "We miss you — 60 days",
                    "It's been a while! Enjoy 15% off your next visit with code COMEBACK15.", null),
            new CampaignDef(92, CampaignKind.FESTIVE, "New Year Party Looks",
                    "Party season is here. Book a blow dry or party makeup and walk in ready.", null),
            new CampaignDef(135, CampaignKind.FESTIVE, "Valentine's Couple Spa",
                    "Treat your partner — couple massages and spa pedicures this week.", null),
            new CampaignDef(155, CampaignKind.WINBACK, "Come back for a Hair Spa",
                    "Your hair misses us! 15% off any hair spa with code COMEBACK15.", null),
            new CampaignDef(205, CampaignKind.FESTIVE, "Wedding Season Bridal",
                    "Bridal and pre-bridal slots are filling fast — reserve yours today.", null),
            new CampaignDef(232, CampaignKind.MEMBERSHIP_RENEWAL, "Renew your Aura membership",
                    "Your membership ends soon. Renew this month and keep saving on every visit.", null),
            new CampaignDef(272, CampaignKind.FESTIVE, "Monsoon Hair Rescue",
                    "Frizz season? Get 20% off keratin and hair spa treatments this month.", null),
            new CampaignDef(300, CampaignKind.WINBACK, "We miss you — 60 days",
                    "It's been a while! Enjoy 15% off your next visit with code COMEBACK15.", null),
            new CampaignDef(326, CampaignKind.BRANCH_WINBACK, "Whitefield — we've missed you",
                    "We've made changes at Aura Whitefield. Come back for 20% off with code COMEBACK15.", "WHF"),
            new CampaignDef(343, CampaignKind.WINBACK, "We miss you — 60 days",
                    "It's been a while! Enjoy 15% off your next visit with code COMEBACK15.", null)
    );

    static final List<String> FEMALE_NAMES = List.of(
            "Aditi", "Ananya", "Aishwarya", "Akshata", "Anjali", "Aparna", "Bhavya", "Chaitra", "Deepika", "Divya",
            "Gayatri", "Harini", "Ishani", "Jahnavi", "Kavitha", "Keerthi", "Lavanya", "Madhuri", "Meera", "Megha",
            "Nandita", "Nikita", "Nisha", "Pallavi", "Pooja", "Priya", "Radhika", "Rashmi", "Riddhi", "Ritu",
            "Sahana", "Sakshi", "Shalini", "Shreya", "Sindhu", "Smita", "Sneha", "Sowmya", "Swati", "Tanya",
            "Trisha", "Vaishnavi", "Varsha", "Vidya", "Zara", "Aisha", "Mariam", "Sara", "Neha", "Kriti");

    static final List<String> MALE_NAMES = List.of(
            "Aakash", "Abhishek", "Aditya", "Ajay", "Akhil", "Amit", "Anand", "Anil", "Arun", "Ashwin",
            "Bharath", "Chetan", "Darshan", "Dev", "Gaurav", "Harsha", "Karan", "Kiran", "Kunal", "Manish",
            "Mohit", "Naveen", "Nikhil", "Nitin", "Pranav", "Rahul", "Rajesh", "Rakesh", "Rohan", "Sachin",
            "Sandeep", "Sanjay", "Shashank", "Siddharth", "Srinivas", "Sunil", "Tarun", "Varun", "Vinay", "Vivek",
            "Yash", "Zaid", "Faisal", "Joel", "Kevin", "Imran", "Sameer", "Deepak", "Ravi", "Suraj");

    static final List<String> SURNAMES = List.of(
            "Acharya", "Agarwal", "Bhat", "Chandra", "Desai", "D'Souza", "Fernandes", "Gowda", "Gupta", "Hegde",
            "Iyer", "Jain", "Joshi", "Kamath", "Kapoor", "Khan", "Kulkarni", "Kumar", "Malhotra", "Menon",
            "Mishra", "Murthy", "Nair", "Naidu", "Patel", "Pillai", "Prasad", "Rao", "Reddy", "Saxena",
            "Sen", "Shah", "Sharma", "Shenoy", "Shetty", "Singh", "Srinivasan", "Thomas", "Varma", "Verma");

    static final List<String> FIVE_STAR_COMMENTS = List.of(
            "Loved my haircut, exactly what I asked for.", "Very hygienic and the staff were lovely.",
            "Best hair spa I've had in Bangalore.", "Quick, professional and great value.",
            "My go-to salon now. Always consistent.", "The facial was so relaxing, skin feels amazing.",
            "Booked on WhatsApp, zero wait time. Great experience.", "", "", "");

    static final List<String> MID_COMMENTS = List.of(
            "Good service but had to wait 20 minutes.", "Haircut was fine, a bit rushed.",
            "Nice ambience, pricing slightly high.", "Okay experience overall.", "", "");

    static final List<String> LOW_COMMENTS = List.of(
            "Waited 40 minutes even with an appointment.", "Stylist seemed in a hurry, not happy with the cut.",
            "Front desk was not attentive.", "Pedicure station wasn't clean enough.",
            "Charged more than what was quoted.", "");
}
