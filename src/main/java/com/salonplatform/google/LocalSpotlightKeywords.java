package com.salonplatform.google;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.enums.BranchBusinessType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class LocalSpotlightKeywords {

    private LocalSpotlightKeywords() {}

    public static BranchBusinessType effectiveType(Branch branch) {
        if (branch.getBusinessType() != null) {
            return branch.getBusinessType();
        }
        return inferFromName(branch.getName());
    }

    public static String resolveLocality(Branch branch) {
        if (branch.getSocietyDefault() != null && !branch.getSocietyDefault().isBlank()) {
            return branch.getSocietyDefault().trim();
        }
        if (branch.getAddress() != null && !branch.getAddress().isBlank()) {
            String[] parts = branch.getAddress().split(",");
            return parts.length > 0 ? parts[parts.length - 1].trim() : branch.getAddress().trim();
        }
        return branch.getName() != null ? branch.getName().trim() : "";
    }

    public static List<String> searchKeywords(Branch branch) {
        String locality = resolveLocality(branch);
        if (locality.isBlank()) {
            return List.of();
        }
        BranchBusinessType type = effectiveType(branch);
        Set<String> keywords = new LinkedHashSet<>();

        switch (type) {
            case SALON -> {
                keywords.add("salon near " + locality);
                keywords.add("hair salon " + locality + " Bangalore");
                keywords.add("best salon " + locality);
                keywords.add("unisex salon " + locality);
            }
            case SPA -> {
                keywords.add("spa near " + locality);
                keywords.add("body spa " + locality + " Bangalore");
                keywords.add("best spa " + locality);
                keywords.add("wellness spa " + locality);
            }
            case SALON_AND_SPA -> {
                keywords.add("salon near " + locality);
                keywords.add("spa near " + locality);
                keywords.add("salon and spa " + locality);
                keywords.add("salon spa " + locality + " Bangalore");
                keywords.add("hair salon " + locality + " Bangalore");
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

    private static BranchBusinessType inferFromName(String name) {
        if (name == null || name.isBlank()) {
            return BranchBusinessType.SALON;
        }
        String normalized = name.toLowerCase();
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
