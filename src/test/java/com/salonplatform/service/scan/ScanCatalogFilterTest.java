package com.salonplatform.service.scan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScanCatalogFilterTest {

    @Test
    void faceRejectsHairAndPedicure() {
        assertFalse(ScanCatalogFilter.isEligible(ScanCatalogFilter.Domain.FACE, "Global Hair Colour", "Hair Colour"));
        assertFalse(ScanCatalogFilter.isEligible(ScanCatalogFilter.Domain.FACE, "Spa Pedicure", "Nails & Pedicure"));
        assertTrue(ScanCatalogFilter.isEligible(ScanCatalogFilter.Domain.FACE, "VLCC Skin Glow Facial", "Skin & Facials"));
    }

    @Test
    void scalpRejectsFacialAndPedicure() {
        assertFalse(ScanCatalogFilter.isEligible(ScanCatalogFilter.Domain.SCALP, "Fruit Facial", "Skin & Facials"));
        assertFalse(ScanCatalogFilter.isEligible(ScanCatalogFilter.Domain.SCALP, "Classic Pedicure", "Nails & Pedicure"));
        assertTrue(ScanCatalogFilter.isEligible(ScanCatalogFilter.Domain.SCALP, "Hair Botox Treatment", "Hair Treatments"));
        assertTrue(ScanCatalogFilter.isEligible(ScanCatalogFilter.Domain.SCALP, "Loreal Hair Colour", "Hair Colour"));
    }
}
