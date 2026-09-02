package com.salonplatform.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignTemplateCatalogTest {

    @Test
    void catalogHasThirtyTemplatesAcrossSixCategories() {
        assertEquals(30, CampaignTemplateCatalog.all().size());
        assertEquals(6, CampaignTemplateCatalog.categoryOrder().size());
        for (var category : CampaignTemplateCatalog.categoryOrder()) {
            int minExpected = category == com.salonplatform.domain.enums.CampaignTemplateCategoryCode.VIP_BEHAVIOURAL ? 3 : 4;
            assertTrue(CampaignTemplateCatalog.byCategory(category).size() >= minExpected,
                    "Expected at least " + minExpected + " templates in " + category);
        }
    }

    @Test
    void eachTemplateHasUniqueIdAndSuggestedMessage() {
        long distinctIds = CampaignTemplateCatalog.all().stream()
                .map(CampaignTemplateDefinition::getId)
                .distinct()
                .count();
        assertEquals(30, distinctIds);
        CampaignTemplateCatalog.all().forEach(t -> {
            assertTrue(t.getSuggestedMessage() != null && !t.getSuggestedMessage().isBlank());
            assertTrue(t.getFilterPreset() != null);
        });
    }
}
