package com.salonplatform.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/** Typical ticket hints for staff sales-boost recommendations (INR). */
public final class StaffSalesBoostCatalog {

    public enum Track {
        HAIR,
        BEAUTY_SPA,
        GENERAL
    }

    public record CatalogEntry(String serviceName, BigDecimal typicalAmount) {}

    private static final List<CatalogEntry> HAIR = List.of(
            new CatalogEntry("Haircut", bd(850)),
            new CatalogEntry("Hair Colour", bd(3500)),
            new CatalogEntry("Hair Spa", bd(1500)),
            new CatalogEntry("Keratin Treatment", bd(6500)),
            new CatalogEntry("Blow Dry", bd(650)),
            new CatalogEntry("Root Touch-up", bd(1200)),
            new CatalogEntry("Beard Grooming", bd(450)));

    private static final List<CatalogEntry> BEAUTY_SPA = List.of(
            new CatalogEntry("Classic Facial", bd(1200)),
            new CatalogEntry("Cleanup", bd(800)),
            new CatalogEntry("De-tan", bd(1500)),
            new CatalogEntry("Full Body Wax", bd(2200)),
            new CatalogEntry("Body Massage", bd(2800)),
            new CatalogEntry("Manicure", bd(650)),
            new CatalogEntry("Pedicure", bd(950)),
            new CatalogEntry("Threading", bd(150)));

    private static final List<CatalogEntry> GENERAL = List.of(
            new CatalogEntry("Haircut", bd(850)),
            new CatalogEntry("Classic Facial", bd(1200)),
            new CatalogEntry("Hair Spa", bd(1500)),
            new CatalogEntry("Cleanup", bd(800)),
            new CatalogEntry("Body Massage", bd(2800)));

    private StaffSalesBoostCatalog() {}

    public static Track trackForDesignation(String designation) {
        if (designation == null || designation.isBlank()) {
            return Track.GENERAL;
        }
        String d = designation.toLowerCase(Locale.ROOT);
        if (d.contains("hair") || d.contains("hairdresser")) {
            return Track.HAIR;
        }
        if (d.contains("beautician")) {
            return Track.BEAUTY_SPA;
        }
        return Track.GENERAL;
    }

    public static List<CatalogEntry> entriesFor(Track track) {
        return switch (track) {
            case HAIR -> HAIR;
            case BEAUTY_SPA -> BEAUTY_SPA;
            case GENERAL -> GENERAL;
        };
    }

    /** Combo / membership packages — recommended for every staff member. */
    private static final List<CatalogEntry> PACKAGES = List.of(
            new CatalogEntry("Monthly Care Package", bd(4500)),
            new CatalogEntry("Hair + Spa Combo Package", bd(5500)),
            new CatalogEntry("Bridal / Occasion Package", bd(12000)));

    public static List<CatalogEntry> packageEntries() {
        return PACKAGES;
    }

    private static BigDecimal bd(long rupees) {
        return BigDecimal.valueOf(rupees);
    }
}
