package com.salonplatform.session;

/** Human-readable device summary derived from a User-Agent string. */
public record DeviceInfo(String browser, String os, String deviceType, String label) {

    public static DeviceInfo unknown() {
        return new DeviceInfo(null, null, "DESKTOP", "Unknown device");
    }
}
