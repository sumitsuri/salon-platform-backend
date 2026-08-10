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

    @Test
    void spaCategoryHasOnlySessionAndPackageServices() {
        var spa = MysticWellnessVarthurRateCardCatalog.all().stream()
                .filter(top -> "Spa".equals(top.name()))
                .findFirst()
                .orElseThrow();
        var subNames = spa.subs().stream().map(RateCardCatalog.SubCategoryDef::name).toList();
        assertTrue(subNames.stream().allMatch(n -> n.startsWith("SPA")));
        assertTrue(subNames.contains("SPA · 60 min"));
        assertTrue(subNames.contains("SPA Package · 5 Sittings / 4 Months"));
    }
}
