package com.salonplatform.google;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.enums.BranchBusinessType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void searchKeywords_useGeographicLocality() {
        Branch branch = branch(
                "Mystic Varthur",
                "SLV Sunrise, Varthur, Bangalore",
                "Mystic Varthur",
                BranchBusinessType.SALON_AND_SPA);

        List<String> keywords = LocalSpotlightKeywords.searchKeywords(branch);

        assertTrue(keywords.contains("salon near Varthur"));
        assertTrue(keywords.contains("spa near Varthur"));
        assertTrue(keywords.contains("hair salon Varthur Bangalore"));
        assertFalse(keywords.stream().anyMatch(k -> k.contains("Mystic")));
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
    void resolveCity_readsCityFromAddress() {
        Branch branch = branch(
                "Mystic Varthur",
                "SLV Sunrise, Varthur, Bangalore",
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
