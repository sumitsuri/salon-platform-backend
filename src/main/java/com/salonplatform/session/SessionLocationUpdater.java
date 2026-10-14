package com.salonplatform.session;

import com.salonplatform.domain.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Fills in a session's location after the response has gone out, so sign-in never waits on a geo lookup. */
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionLocationUpdater {

    private final GeoIpService geoIpService;
    private final RefreshTokenRepository repository;

    @Async
    public void resolveForLogin(UUID sessionId, String ip) {
        try {
            geoIpService.locate(ip).ifPresent(loc -> repository.setLoginLocation(sessionId, loc));
        } catch (Exception e) {
            log.debug("Could not store login location for session {}: {}", sessionId, e.getMessage());
        }
    }

    @Async
    public void resolveForLastSeen(UUID sessionId, String ip) {
        try {
            geoIpService.locate(ip).ifPresent(loc -> repository.setLastLocation(sessionId, loc));
        } catch (Exception e) {
            log.debug("Could not store last-seen location for session {}: {}", sessionId, e.getMessage());
        }
    }
}
