package com.salonplatform.google;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.enums.BranchBusinessType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

class LocalSpotlightKeywordsTest {

    @Test
    void resolveLocality_usesNeighbourhoodFromAddress_notBranchName() {
        Branch branch = branch(
                "Mystic Varthur",
                "SLV Sunrise, Varthur, Bangalore",
                "Mystic Varthur",
                BranchBusinessType.SALON_AND_SPA);

        assertEquals("Varthur", LocalSpotlightKeywords.resolveLocality(branch));
    }

    @Test
    void resolvePinCode_readsSixDigitPinFromAddress() {
        Branch branch = branch(
                "Mystic Varthur",
                "SLV Sunrise, Varthur, Bangalore 560087",
                "Mystic Varthur",
                BranchBusinessType.SALON_AND_SPA);

        assertEquals("560087", LocalSpotlightKeywords.resolvePinCode(branch));
    }

    @Test
    void searchKeywords_dedupeCaseInsensitivePinTerms() {
        Branch branch = branch(
                "Mystic Varthur",
                "SLV Sunrise, Varthur, Bangalore 560087",
                "Mystic Varthur",
                BranchBusinessType.SALON_AND_SPA);

        List<String> keywords = LocalSpotlightKeywords.searchKeywords(branch);
        Set<String> normalized = new HashSet<>();
        for (String keyword : keywords) {
            assertTrue(normalized.add(keyword.trim().toLowerCase(Locale.ROOT)), "duplicate keyword: " + keyword);
        }
        long spaNearPin = keywords.stream()
                .filter(k -> k.equalsIgnoreCase("spa near 560087"))
                .count();
        assertEquals(1, spaNearPin);
    }

    @Test
    void searchKeywords_usePinCodeNotLocalityName() {
        Branch branch = branch(
                "Mystic Varthur",
                "SLV Sunrise, Varthur, Bangalore 560087",
                "Mystic Varthur",
                BranchBusinessType.SALON_AND_SPA);

        List<String> keywords = LocalSpotlightKeywords.searchKeywords(branch);

        assertTrue(keywords.contains("beauty salons in 560087"));
        assertTrue(keywords.contains("Spa and salon in 560087"));
        assertTrue(keywords.contains("Hair Salon near me"));
        assertFalse(keywords.stream().anyMatch(k -> k.contains("Varthur")));
        assertFalse(keywords.stream().anyMatch(k -> k.contains("Mystic")));
    }

    @Test
    void serpCacheKey_sharesPinAcrossBranches() {
        Branch a = branch("A", "Addr 560087", null, BranchBusinessType.SPA);
        Branch b = branch("B", "Other, 560087", null, BranchBusinessType.SPA);
        a.setId(java.util.UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        b.setId(java.util.UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));

        String keyword = "spa in 560087";
        assertEquals(
                LocalSpotlightKeywords.serpCacheKey(a, keyword),
                LocalSpotlightKeywords.serpCacheKey(b, keyword));
    }

    @Test
    void serpCacheKey_nearMeIsPerBranch() {
        Branch a = branch("A", "Addr 560087", null, BranchBusinessType.SPA);
        Branch b = branch("B", "Other, 560087", null, BranchBusinessType.SPA);
        a.setId(java.util.UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        b.setId(java.util.UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));

        String keyword = "Spa near me";
        assertFalse(
                LocalSpotlightKeywords.serpCacheKey(a, keyword)
                        .equals(LocalSpotlightKeywords.serpCacheKey(b, keyword)));
    }

    @Test
    void resolveLocality_fallsBackToSocietyWhenAddressMissing() {
        Branch branch = branch("Indiranagar Studio", null, "Indiranagar", BranchBusinessType.SALON);

        assertEquals("Indiranagar", LocalSpotlightKeywords.resolveLocality(branch));
    }

    @Test
    void resolveLocality_skipsSocietyWhenItMatchesBranchName() {
        Branch branch = branch("Mystic Varthur", null, "Mystic Varthur", BranchBusinessType.SALON_AND_SPA);

        assertEquals("Mystic Varthur", LocalSpotlightKeywords.resolveLocality(branch));
    }

    @Test
    void rankKeywordsMatchStored_rejectsLegacyLocalityKeywords() {
        Branch branch = branch(
                "Varthur",
                "SLV Sunrise, Varthur, Bangalore 560087",
                "SLV Sunrise",
                BranchBusinessType.SALON_AND_SPA);

        assertFalse(LocalSpotlightKeywords.rankKeywordsMatchStored(
                List.of("salon near Varthur"), branch));
        assertTrue(LocalSpotlightKeywords.rankKeywordsMatchStored(
                LocalSpotlightKeywords.searchKeywords(branch), branch));
    }

    @Test
    void resolveCity_readsCityFromAddress() {
        Branch branch = branch(
                "Mystic Varthur",
                "SLV Sunrise, Varthur, Bangalore 560087",
                "Varthur",
                BranchBusinessType.SALON_AND_SPA);

        assertEquals("Bangalore", LocalSpotlightKeywords.resolveCity(branch));
    }

    private static Branch branch(String name, String address, String societyDefault, BranchBusinessType type) {
        Branch branch = new Branch();
        branch.setName(name);
        branch.setAddress(address);
        branch.setSocietyDefault(societyDefault);
        branch.setBusinessType(type);
        return branch;
    }
}
