package com.salonplatform.google;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.enums.BranchBusinessType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LocalSpotlightKeywords {

    private static final Pattern INDIAN_PIN = Pattern.compile("\\b([1-9][0-9]{5})\\b");

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

    /** Six-digit Indian PIN from the branch address (last match wins). Required for keyword templates. */
    public static String resolvePinCode(Branch branch) {
        if (branch == null || branch.getAddress() == null || branch.getAddress().isBlank()) {
            return "";
        }
        Matcher matcher = INDIAN_PIN.matcher(branch.getAddress());
        String pin = "";
        while (matcher.find()) {
            pin = matcher.group(1);
        }
        return pin;
    }

    public static boolean isNearMeKeyword(String keyword) {
        return keyword != null && keyword.toLowerCase(Locale.ROOT).contains(" near me");
    }

    /**
     * SERP cache key: shared by PIN for geo keywords; per-branch for "near me" (location-biased).
     */
    public static String serpCacheKey(Branch branch, String keyword) {
        if (isNearMeKeyword(keyword)) {
            return "BRANCH:" + branch.getId() + "|" + keyword.trim().toLowerCase(Locale.ROOT);
        }
        String pin = resolvePinCode(branch);
        return "PIN:" + pin + "|" + keyword.trim().toLowerCase(Locale.ROOT);
    }

    /** City suffix for keyword templates such as "hair salon 560087 Bangalore". */
    public static String resolveCity(Branch branch) {
        String fromAddress = extractCityFromAddress(branch.getAddress());
        if (!fromAddress.isBlank()) {
            return fromAddress;
        }
        return "Bangalore";
    }

    /** True when persisted Google rank rows match the keywords we would generate today. */
    public static boolean rankKeywordsMatchStored(List<String> storedKeywords, Branch branch) {
        if (storedKeywords == null || storedKeywords.isEmpty()) {
            return true;
        }
        List<String> expected = searchKeywords(branch);
        if (storedKeywords.size() != expected.size()) {
            return false;
        }
        for (int i = 0; i < expected.size(); i++) {
            String stored = storedKeywords.get(i);
            if (stored == null || !stored.equalsIgnoreCase(expected.get(i))) {
                return false;
            }
        }
        return true;
    }

    public static List<String> searchKeywords(Branch branch) {
        String pin = resolvePinCode(branch);
        if (pin.isBlank()) {
            return List.of();
        }
        String city = resolveCity(branch);
        BranchBusinessType type = effectiveType(branch);
        Set<String> keywords = new LinkedHashSet<>();
        Set<String> seenNormalized = new HashSet<>();
        addPinKeywords(keywords, seenNormalized, pin, city, type);
        addNearMeKeywords(keywords, seenNormalized, type);
        return new ArrayList<>(keywords);
    }

    /** Google text search treats queries case-insensitively — keep one canonical row per term. */
    private static void addKeyword(Set<String> keywords, Set<String> seenNormalized, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        if (seenNormalized.add(normalized)) {
            keywords.add(keyword.trim());
        }
    }

    private static void addPinKeywords(
            Set<String> keywords, Set<String> seenNormalized, String pin, String city, BranchBusinessType type) {
        switch (type) {
            case SALON -> {
                addKeyword(keywords, seenNormalized, "beauty salons in " + pin);
                addKeyword(keywords, seenNormalized, "Hair Salon in " + pin);
                addKeyword(keywords, seenNormalized, "premium salon in " + pin);
                addKeyword(keywords, seenNormalized, "Luxury Salon in " + pin);
                addKeyword(keywords, seenNormalized, "Grooming salon in " + pin);
                addKeyword(keywords, seenNormalized, "salon near " + pin);
                addKeyword(keywords, seenNormalized, "hair salon " + pin + " " + city);
                addKeyword(keywords, seenNormalized, "best salon " + pin);
                addKeyword(keywords, seenNormalized, "unisex salon " + pin);
                addKeyword(keywords, seenNormalized, "Waxing salon in " + pin);
                addKeyword(keywords, seenNormalized, "advanced hair coloring in " + pin);
            }
            case SPA -> {
                addKeyword(keywords, seenNormalized, "spa in " + pin);
                addKeyword(keywords, seenNormalized, "Luxury spa in " + pin);
                addKeyword(keywords, seenNormalized, "Spa near " + pin);
                addKeyword(keywords, seenNormalized, "Premium spa in " + pin);
                addKeyword(keywords, seenNormalized, "Body Spa near " + pin);
                addKeyword(keywords, seenNormalized, "Body Massage in " + pin);
                addKeyword(keywords, seenNormalized, "body spa " + pin + " " + city);
                addKeyword(keywords, seenNormalized, "best spa " + pin);
                addKeyword(keywords, seenNormalized, "wellness spa " + pin);
            }
            case SALON_AND_SPA -> {
                addKeyword(keywords, seenNormalized, "beauty salons in " + pin);
                addKeyword(keywords, seenNormalized, "Hair Salon in " + pin);
                addKeyword(keywords, seenNormalized, "premium salon in " + pin);
                addKeyword(keywords, seenNormalized, "Luxury Salon in " + pin);
                addKeyword(keywords, seenNormalized, "Grooming salon in " + pin);
                addKeyword(keywords, seenNormalized, "salon near " + pin);
                addKeyword(keywords, seenNormalized, "Spa near " + pin);
                addKeyword(keywords, seenNormalized, "spa in " + pin);
                addKeyword(keywords, seenNormalized, "Luxury spa in " + pin);
                addKeyword(keywords, seenNormalized, "Premium spa in " + pin);
                addKeyword(keywords, seenNormalized, "Spa and salon in " + pin);
                addKeyword(keywords, seenNormalized, "Body Spa near " + pin);
                addKeyword(keywords, seenNormalized, "Body Massage in " + pin);
                addKeyword(keywords, seenNormalized, "salon and spa " + pin);
                addKeyword(keywords, seenNormalized, "salon spa " + pin + " " + city);
                addKeyword(keywords, seenNormalized, "hair salon " + pin + " " + city);
                addKeyword(keywords, seenNormalized, "best salon " + pin);
                addKeyword(keywords, seenNormalized, "best spa " + pin);
                addKeyword(keywords, seenNormalized, "unisex salon " + pin);
                addKeyword(keywords, seenNormalized, "Waxing salon in " + pin);
                addKeyword(keywords, seenNormalized, "Skin care salon in " + pin);
                addKeyword(keywords, seenNormalized, "advanced hair coloring in " + pin);
            }
        }
    }

    private static void addNearMeKeywords(Set<String> keywords, Set<String> seenNormalized, BranchBusinessType type) {
        switch (type) {
            case SALON -> addKeyword(keywords, seenNormalized, "Hair Salon near me");
            case SPA -> addKeyword(keywords, seenNormalized, "Spa near me");
            case SALON_AND_SPA -> {
                addKeyword(keywords, seenNormalized, "Hair Salon near me");
                addKeyword(keywords, seenNormalized, "Spa near me");
            }
        }
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
