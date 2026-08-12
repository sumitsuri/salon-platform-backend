package com.salonplatform.google;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DigitalPresenceMatchingTest {

    @Test
    void extractAddressLandmark_usesFirstSegment() {
        assertEquals(
                "SLV sunrise",
                DigitalPresenceSyncService.extractAddressLandmark(
                        "SLV sunrise, 2nd floor, varthur, bengaluru, 560087"));
    }

    @Test
    void extractAddressLandmark_blankWhenMissing() {
        assertEquals("", DigitalPresenceSyncService.extractAddressLandmark(null));
        assertEquals("", DigitalPresenceSyncService.extractAddressLandmark("   "));
    }
}
