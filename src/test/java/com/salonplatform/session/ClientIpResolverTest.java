package com.salonplatform.session;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClientIpResolverTest {

    @Test
    void takesTheEntryTheTrustedProxiesAppended() {
        // ALB appended the client, nginx appended the ALB. A spoofed leftmost value must be ignored.
        assertEquals("49.37.1.2", ClientIpResolver.pickForwarded("6.6.6.6, 49.37.1.2, 10.0.1.5", 2));
        assertEquals("49.37.1.2", ClientIpResolver.pickForwarded("49.37.1.2, 10.0.1.5", 2));
    }

    @Test
    void shortChainFallsBackToTheFirstEntry() {
        assertEquals("49.37.1.2", ClientIpResolver.pickForwarded("49.37.1.2", 2));
    }

    @Test
    void ignoresTheHeaderWhenNoProxyIsTrusted() {
        assertNull(ClientIpResolver.pickForwarded("6.6.6.6", 0));
    }

    @Test
    void usesTheSocketAddressWithoutProxies() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("203.0.113.9");
        req.addHeader("X-Forwarded-For", "6.6.6.6");
        assertEquals("203.0.113.9", new ClientIpResolver(0).resolve(req, "Employee").ip());
        assertEquals("employee", new ClientIpResolver(0).resolve(req, "Employee").portal());
    }

    @Test
    void rejectsGarbageAddresses() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("203.0.113.9");
        req.addHeader("X-Forwarded-For", "x'; drop table users;--");
        assertNull(new ClientIpResolver(1).resolve(req, null).ip());
    }
}
