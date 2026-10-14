package com.salonplatform.session;

import java.util.Locale;

/**
 * Small, dependency-free User-Agent reader. It only needs to be good enough to tell a person "Chrome on Windows" or
 * "Safari on iPhone" so they can recognise their own devices; it is not used for any security decision.
 */
public final class UserAgentParser {

    private UserAgentParser() {}

    public static DeviceInfo parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return DeviceInfo.unknown();
        }
        String ua = userAgent;
        String lower = ua.toLowerCase(Locale.ROOT);

        String os = detectOs(ua, lower);
        String browser = detectBrowser(ua, lower);
        String deviceType = detectDeviceType(lower);

        String label;
        if (browser == null && os == null) {
            label = "Unknown device";
        } else if (browser == null) {
            label = os;
        } else if (os == null) {
            label = browser;
        } else {
            label = browser + " on " + os;
        }
        return new DeviceInfo(browser, os, deviceType, label);
    }

    private static String detectOs(String ua, String lower) {
        if (lower.contains("iphone") || lower.contains("ipod")) return "iPhone";
        if (lower.contains("ipad")) return "iPad";
        if (lower.contains("android")) return "Android";
        if (lower.contains("windows")) return "Windows";
        if (lower.contains("cros")) return "ChromeOS";
        if (lower.contains("mac os x") || lower.contains("macintosh")) return "macOS";
        if (lower.contains("linux") || lower.contains("x11")) return "Linux";
        return null;
    }

    private static String detectBrowser(String ua, String lower) {
        // Order matters: most browsers also announce "Chrome" and "Safari".
        if (lower.contains("edg/") || lower.contains("edga/") || lower.contains("edgios/")) return "Edge";
        if (lower.contains("opr/") || lower.contains("opera")) return "Opera";
        if (lower.contains("samsungbrowser")) return "Samsung Internet";
        if (lower.contains("firefox/") || lower.contains("fxios/")) return "Firefox";
        if (lower.contains("crios/")) return "Chrome";
        if (lower.contains("chrome/")) return "Chrome";
        if (lower.contains("safari/") && lower.contains("version/")) return "Safari";
        if (lower.contains("okhttp") || lower.contains("dart:") || lower.contains("cfnetwork")) return "App";
        return null;
    }

    private static String detectDeviceType(String lower) {
        if (lower.contains("ipad") || lower.contains("tablet")) return "TABLET";
        if (lower.contains("android") && !lower.contains("mobile")) return "TABLET";
        if (lower.contains("iphone") || lower.contains("ipod") || lower.contains("mobi") || lower.contains("android")) {
            return "MOBILE";
        }
        return "DESKTOP";
    }
}
