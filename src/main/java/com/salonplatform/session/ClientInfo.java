package com.salonplatform.session;

/** What we know about the device making a sign-in or refresh request. Every field may be null. */
public record ClientInfo(String ip, String userAgent, String portal) {

    public static ClientInfo unknown() {
        return new ClientInfo(null, null, null);
    }

    public ClientInfo withPortal(String newPortal) {
        return new ClientInfo(ip, userAgent, newPortal);
    }
}
