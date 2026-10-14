package com.salonplatform.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserAgentParserTest {

    @Test
    void chromeOnWindows() {
        DeviceInfo d = UserAgentParser.parse(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36");
        assertEquals("Chrome on Windows", d.label());
        assertEquals("DESKTOP", d.deviceType());
    }

    @Test
    void safariOnIphoneIsMobile() {
        DeviceInfo d = UserAgentParser.parse(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1");
        assertEquals("Safari on iPhone", d.label());
        assertEquals("MOBILE", d.deviceType());
    }

    @Test
    void chromeOnAndroidPhoneVersusTablet() {
        assertEquals("MOBILE", UserAgentParser.parse(
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36").deviceType());
        assertEquals("TABLET", UserAgentParser.parse(
                "Mozilla/5.0 (Linux; Android 13; SM-X700) AppleWebKit/537.36 Chrome/126.0 Safari/537.36").deviceType());
    }

    @Test
    void edgeIsNotReportedAsChrome() {
        assertEquals("Edge on Windows", UserAgentParser.parse(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126.0 Safari/537.36 Edg/126.0").label());
    }

    @Test
    void chromeOnIosAndFirefoxOnMac() {
        assertEquals("Chrome on iPhone", UserAgentParser.parse(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 CriOS/126.0 Mobile/15E148 Safari/604.1").label());
        assertEquals("Firefox on macOS", UserAgentParser.parse(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:127.0) Gecko/20100101 Firefox/127.0").label());
    }

    @Test
    void missingOrUnrecognisedAgentFallsBack() {
        assertEquals("Unknown device", UserAgentParser.parse(null).label());
        assertEquals("Unknown device", UserAgentParser.parse("  ").label());
        assertEquals("Unknown device", UserAgentParser.parse("curl/8.4.0").label());
        assertNull(UserAgentParser.parse("curl/8.4.0").browser());
    }
}
