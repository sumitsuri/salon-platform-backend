package com.salonplatform.service;

import com.salonplatform.dto.analytics.LocalSpotlightSyncProgressResponse;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalSpotlightSyncProgressServiceTest {

    @Test
    void tracksKeywordProgressThenClearsOnFinish() {
        LocalSpotlightSyncProgressService service = new LocalSpotlightSyncProgressService();
        UUID tenantId = UUID.randomUUID();
        service.start(tenantId, 3);
        service.keywordStep(tenantId, 2, 3, "salon near 560087");

        LocalSpotlightSyncProgressResponse snap =
                service.snapshot(tenantId).orElseThrow();
        assertTrue(snap.isActive());
        assertTrue(snap.getPercent() > 0);

        service.finish(tenantId);
        assertFalse(service.isActive(tenantId));
        assertFalse(service.snapshot(tenantId).orElseThrow().isActive());
    }
}
