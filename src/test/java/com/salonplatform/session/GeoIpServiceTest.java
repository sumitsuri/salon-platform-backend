package com.salonplatform.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoIpServiceTest {

    @Test
    void formatsAndDeduplicatesParts() {
        assertEquals("Bengaluru, Karnataka, India", GeoIpService.formatLocation("Bengaluru", "Karnataka", "India"));
        assertEquals("Singapore", GeoIpService.formatLocation("Singapore", "Singapore", null));
        assertNull(GeoIpService.formatLocation(null, "", " "));
    }

    @Test
    void privateAddressesNeverLeaveTheMachine() {
        GeoIpService disabled = new GeoIpService(false, "http://invalid.invalid/{ip}", 100);
        assertEquals(GeoIpService.LOCAL_NETWORK, disabled.locate("127.0.0.1").orElseThrow());
        assertEquals(GeoIpService.LOCAL_NETWORK, disabled.locate("192.168.1.20").orElseThrow());
        assertEquals(GeoIpService.LOCAL_NETWORK, disabled.locate("10.0.1.5").orElseThrow());
        assertEquals(GeoIpService.LOCAL_NETWORK, disabled.locate("::1").orElseThrow());
    }

    @Test
    void publicAddressIsNotLookedUpWhenDisabled() {
        assertTrue(new GeoIpService(false, "http://invalid.invalid/{ip}", 100).locate("49.37.1.2").isEmpty());
    }

    @Test
    void unreachableProviderDegradesToNoLocation() {
        assertTrue(new GeoIpService(true, "http://127.0.0.1:9/{ip}", 200).locate("49.37.1.2").isEmpty());
    }

    @Test
    void nonAddressInputIsIgnored() {
        assertTrue(new GeoIpService(true, "http://127.0.0.1:9/{ip}", 200).locate("example.com").isEmpty());
        assertTrue(new GeoIpService(true, "http://127.0.0.1:9/{ip}", 200).locate(null).isEmpty());
    }
}
