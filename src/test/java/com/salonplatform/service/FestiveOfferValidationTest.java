package com.salonplatform.service;

import com.salonplatform.dto.offerposter.OfferBlockDto;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.offerposter.OfferBlockType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.salonplatform.offerposter.OfferCopyTest.*;
import static org.junit.jupiter.api.Assertions.*;

class FestiveOfferValidationTest {

    private static List<OfferBlockDto> one(OfferBlockDto b) {
        return new ArrayList<>(List.of(b));
    }

    private static String error(List<OfferBlockDto> blocks) {
        return assertThrows(BadRequestException.class, () -> FestiveOfferService.normaliseBlocks(blocks)).getMessage();
    }

    @Test
    void goodOffersPassAndAreTrimmed() {
        OfferBlockDto b = spend(tier("999", "10", null));
        b.setAppliesTo("  any salon   services ");
        List<OfferBlockDto> out = FestiveOfferService.normaliseBlocks(one(b));
        assertEquals("any salon services", out.get(0).getAppliesTo());
    }

    @Test
    void needsAtLeastOneOfferAndNoMoreThanSix() {
        assertTrue(error(List.of()).contains("at least one"));
        List<OfferBlockDto> seven = new ArrayList<>();
        for (int i = 0; i < 7; i++) seven.add(spend(tier("999", "10", null)));
        assertTrue(error(seven).contains("at most 6"));
    }

    @Test
    void percentagesAreLimitedToSensibleValues() {
        assertTrue(error(one(spend(tier("999", "0", null)))).contains("between 1% and 90%"));
        assertTrue(error(one(spend(tier("999", "95", null)))).contains("between 1% and 90%"));
        assertTrue(error(one(pkg("P", "100", "A", "100", "B", "100"))).contains("between 1% and 90%"));
    }

    @Test
    void spendAmountsMustBePositiveAndDistinct() {
        assertTrue(error(one(spend(tier("0", "10", null)))).contains("at least ₹1"));
        assertTrue(error(one(spend(tier("999", "10", null), tier("999.0", "20", null)))).contains("same spend"));
    }

    @Test
    void freeOffersNeedAnItem() {
        assertTrue(error(one(free(tier("999", null, "  ")))).contains("free"));
    }

    @Test
    void packagesNeedTwoPricedServices() {
        assertTrue(error(one(pkg("P", "20", "Facial", "1000"))).contains("between 2 and 12"));
        assertTrue(error(one(pkg("P", "20", "Facial", "1000", "Spa", "0"))).contains("price above"));
        OfferBlockDto noName = pkg("P", "20", "Facial", "1000", "Spa", "500");
        noName.getItems().get(1).setName(" ");
        assertTrue(error(one(noName)).contains("needs a name"));
    }

    @Test
    void overlongTextIsRejectedRatherThanSilentlyCut() {
        OfferBlockDto b = spend(tier("999", "10", null));
        b.setAppliesTo("x".repeat(80));
        assertTrue(error(one(b)).contains("too long"));
    }

    @Test
    void typeSpecificFieldsDoNotLeakAcrossTypes() {
        OfferBlockDto b = spend(tier("999", "10", "Free thing"));
        b.setItems(new ArrayList<>());
        b.setDiscountPercent(new BigDecimal("50"));
        OfferBlockDto n = FestiveOfferService.normaliseBlocks(one(b)).get(0);
        assertNull(n.getTiers().get(0).getFreeItem());
        assertNull(n.getDiscountPercent());
        assertEquals(OfferBlockType.SPEND_PERCENT, n.getType());
    }
}
