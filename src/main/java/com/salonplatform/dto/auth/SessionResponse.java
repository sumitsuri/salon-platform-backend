package com.salonplatform.dto.auth;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/** One signed-in device, as shown on the Signed-in devices screen. */
@Data
@Builder
public class SessionResponse {
    private UUID id;
    private String deviceLabel;
    /** MOBILE | TABLET | DESKTOP */
    private String deviceType;
    private String browser;
    private String os;
    /** employee | manager | admin | platform — the app signed in to. */
    private String portal;
    private Instant signedInAt;
    private Instant lastActiveAt;
    private String loginIp;
    private String loginLocation;
    private String lastIp;
    /** Where the device was last seen (falls back to where it signed in). */
    private String lastLocation;
    /** True for the device making this request. */
    private boolean current;
}
