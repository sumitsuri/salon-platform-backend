package com.salonplatform.google;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.enums.BranchBusinessType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LocalSpotlightKeywords {

    private static final Set<String> KNOWN_CITIES = Set.of(
            "bangalore", "bengaluru", "mumbai", "delhi", "new delhi", "chennai", "hyderabad",
            "pune", "kolkata", "gurugram", "gurgaon", "noida", "faridabad", "ghaziabad");

    private LocalSpotlightKeywords() {}

    public static BranchBusinessType effectiveType(Branch branch) {
        if (branch.getBusinessType() != null) {
            return branch.getBusinessType();
        }
        return inferFromName(branch.getName());
    }

    /**
     * Geographic area used in Local Spotlight keyword templates (e.g. "salon near Varthur").
     * Prefers the neighbourhood segment from the branch address (before city), not the branch brand name.
     */
    public static String resolveLocality(Branch branch) {
        String fromAddress = extractLocalityFromAddress(branch.getAddress());
        if (!fromAddress.isBlank()) {
            return fromAddress;
        }
        String society = trimToEmpty(branch.getSocietyDefault());
        String name = trimToEmpty(branch.getName());
        if (!society.isBlank() && !localityMatchesBranchName(society, name)) {
            return society;
        }
        return name;
    }

    /** City suffix for keyword templates such as "hair salon Varthur Bangalore". */
    public static String resolveCity(Branch branch) {
        String fromAddress = extractCityFromAddress(branch.getAddress());
        if (!fromAddress.isBlank()) {
            return fromAddress;
        }
        return "Bangalore";
    }

    public static List<String> searchKeywords(Branch branch) {
        String locality = resolveLocality(branch);
        if (locality.isBlank()) {
            return List.of();
        }
        String city = resolveCity(branch);
        BranchBusinessType type = effectiveType(branch);
        Set<String> keywords = new LinkedHashSet<>();

        switch (type) {
            case SALON -> {
                keywords.add("salon near " + locality);
                keywords.add("hair salon " + locality + " " + city);
                keywords.add("best salon " + locality);
                keywords.add("unisex salon " + locality);
            }
            case SPA -> {
                keywords.add("spa near " + locality);
                keywords.add("body spa " + locality + " " + city);
                keywords.add("best spa " + locality);
                keywords.add("wellness spa " + locality);
            }
            case SALON_AND_SPA -> {
                keywords.add("salon near " + locality);
                keywords.add("spa near " + locality);
                keywords.add("salon and spa " + locality);
                keywords.add("salon spa " + locality + " " + city);
                keywords.add("hair salon " + locality + " " + city);
                keywords.add("best salon " + locality);
                keywords.add("best spa " + locality);
                keywords.add("unisex salon " + locality);
            }
        }
        return new ArrayList<>(keywords);
    }

    public static List<String> nearbyPlaceTypes(Branch branch) {
        return switch (effectiveType(branch)) {
            case SALON -> List.of("hair_salon", "beauty_salon");
            case SPA -> List.of("spa", "beauty_salon");
            case SALON_AND_SPA -> List.of("hair_salon", "beauty_salon", "spa");
        };
    }

    /** Primary term appended when matching the branch's own Google listing. */
    public static String primaryListingQueryTerm(Branch branch) {
        return switch (effectiveType(branch)) {
            case SALON -> "salon";
            case SPA -> "spa";
            case SALON_AND_SPA -> "salon spa";
        };
    }

    static String extractLocalityFromAddress(String address) {
        List<String> parts = splitAddressParts(address);
        if (parts.isEmpty()) {
            return "";
        }
        if (parts.size() == 1) {
            return isKnownCity(parts.get(0)) ? "" : parts.get(0);
        }
        String last = parts.get(parts.size() - 1);
        if (isKnownCity(last)) {
            return parts.get(parts.size() - 2);
        }
        return last;
    }

    static String extractCityFromAddress(String address) {
        List<String> parts = splitAddressParts(address);
        if (parts.isEmpty()) {
            return "";
        }
        String last = parts.get(parts.size() - 1);
        return isKnownCity(last) ? capitalizeCity(last) : "";
    }

    static boolean localityMatchesBranchName(String locality, String branchName) {
        if (locality == null || locality.isBlank() || branchName == null || branchName.isBlank()) {
            return false;
        }
        String local = locality.trim().toLowerCase(Locale.ROOT);
        String name = branchName.trim().toLowerCase(Locale.ROOT);
        return local.equals(name);
    }

    private static List<String> splitAddressParts(String address) {
        if (address == null || address.isBlank()) {
            return List.of();
        }
        return Arrays.stream(address.split(","))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .toList();
    }

    private static boolean isKnownCity(String segment) {
        return KNOWN_CITIES.contains(segment.trim().toLowerCase(Locale.ROOT));
    }

    private static String capitalizeCity(String city) {
        if (city == null || city.isBlank()) {
            return "";
        }
        String lower = city.trim().toLowerCase(Locale.ROOT);
        if (lower.equals("bengaluru")) {
            return "Bangalore";
        }
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static BranchBusinessType inferFromName(String name) {
        if (name == null || name.isBlank()) {
            return BranchBusinessType.SALON;
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        boolean salon = normalized.contains("salon");
        boolean spa = normalized.contains("spa");
        if (salon && spa) {
            return BranchBusinessType.SALON_AND_SPA;
        }
        if (spa) {
            return BranchBusinessType.SPA;
        }
        return BranchBusinessType.SALON;
    }
}
