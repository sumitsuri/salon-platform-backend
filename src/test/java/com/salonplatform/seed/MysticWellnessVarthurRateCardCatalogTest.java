package com.salonplatform.seed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MysticWellnessVarthurRateCardCatalogTest {

    @Test
    void catalogHasExpectedServiceVolume() {
        long serviceCount = MysticWellnessVarthurRateCardCatalog.all().stream()
                .flatMap(top -> top.subs().stream())
                .flatMap(sub -> sub.services().stream())
                .count();
        assertTrue(serviceCount >= 120, "Expected full Varthur menu coverage, got " + serviceCount);
    }
}
