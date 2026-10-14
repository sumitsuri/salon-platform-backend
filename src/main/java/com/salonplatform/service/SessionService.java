package com.salonplatform.service;

import com.salonplatform.domain.entity.RefreshToken;
import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.enums.UserRole;
import com.salonplatform.domain.repository.RefreshTokenRepository;
import com.salonplatform.domain.repository.UserRepository;
import com.salonplatform.dto.auth.SessionResponse;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.exception.UnauthorizedException;
import com.salonplatform.security.JwtTokenProvider;
import com.salonplatform.session.ClientInfo;
import com.salonplatform.session.DeviceInfo;
import com.salonplatform.session.SessionLocationUpdater;
import com.salonplatform.session.UserAgentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Signed-in devices. Every sign-in creates one session (a refresh-token row) and leaves the user's other devices
 * alone. A session ends only when the user signs out, revokes it from the devices list, or an admin ends it
 * (password reset, account disabled, role change). Platform staff keep a sliding idle limit; nobody else expires.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    /** How often a device's "last active" time (and IP) is written back. */
    static final Duration TOUCH_INTERVAL = Duration.ofHours(1);

    private final RefreshTokenRepository repository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final SessionLocationUpdater locationUpdater;

    @Value("${app.jwt.max-sessions-per-user:25}")
    private int maxSessionsPerUser;

    public record Validated(RefreshToken session, User user) {}

    /** Roles that keep a sliding idle timeout (internal staff); everyone else stays signed in until they sign out. */
    public static boolean hasIdleLimit(UserRole role) {
        return role == UserRole.PLATFORM_SUPER_ADMIN || role == UserRole.SALES_EXECUTIVE;
    }

    @Transactional
    public RefreshToken open(UUID userId, UserRole role, String rawToken, ClientInfo client) {
        ClientInfo info = client != null ? client : ClientInfo.unknown();
        DeviceInfo device = UserAgentParser.parse(info.userAgent());
        Instant now = Instant.now();

        RefreshToken session = repository.save(RefreshToken.builder()
                .userId(userId)
                .token(rawToken)
                .expiresAt(hasIdleLimit(role) ? now.plusMillis(jwtTokenProvider.getRefreshTokenExpiryMs()) : null)
                .lastUsedAt(now)
                .userAgent(info.userAgent())
                .deviceLabel(device.label())
                .deviceType(device.deviceType())
                .browser(device.browser())
                .os(device.os())
                .portal(info.portal())
                .loginIp(info.ip())
                .lastIp(info.ip())
                .build());

        evictOldest(userId, session.getId());
        if (info.ip() != null) {
            runAfterCommit(() -> locationUpdater.resolveForLogin(session.getId(), info.ip()));
        }
        return session;
    }

    /**
     * Checks a refresh token and returns its session and user. Throws {@link UnauthorizedException} (HTTP 401) only
     * when the session has really ended; every other failure surfaces as a server error so clients keep the user
     * signed in and retry.
     */
    @Transactional
    public Validated validateAndTouch(String rawToken, ClientInfo client) {
        RefreshToken session = repository.findByTokenAndRevokedFalse(rawToken)
                .orElseThrow(() -> new UnauthorizedException("error.session.revoked"));
        User user = userRepository.findById(session.getUserId()).orElse(null);
        if (user == null || !user.isActive()) {
            repository.deleteByTokenValue(rawToken);
            throw new UnauthorizedException("error.session.accountDisabled");
        }

        Instant now = Instant.now();
        boolean dirty = false;
        boolean due = session.getLastUsedAt() == null
                || session.getLastUsedAt().isBefore(now.minus(TOUCH_INTERVAL));

        if (hasIdleLimit(user.getRole())) {
            if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(now)) {
                repository.deleteByTokenValue(rawToken);
                throw new UnauthorizedException("error.refreshToken.expired");
            }
            if (due || session.getExpiresAt() == null) {
                session.setExpiresAt(now.plusMillis(jwtTokenProvider.getRefreshTokenExpiryMs()));
                dirty = true;
            }
        } else if (session.getExpiresAt() != null) {
            // Sessions issued under the old 20-day rule: lift the limit (also revives ones that had already lapsed).
            session.setExpiresAt(null);
            dirty = true;
        }

        String ip = client != null ? client.ip() : null;
        if (due) {
            boolean ipChanged = ip != null && !Objects.equals(ip, session.getLastIp());
            session.setLastUsedAt(now);
            if (ip != null) session.setLastIp(ip);
            dirty = true;
            if (ipChanged) {
                String sessionId = session.getId().toString();
                runAfterCommit(() -> locationUpdater.resolveForLastSeen(UUID.fromString(sessionId), ip));
            }
        }
        if (dirty) {
            session = repository.save(session);
        }
        return new Validated(session, user);
    }

    /** Signs out one device. Safe to call repeatedly or with an unknown token. */
    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        repository.deleteByTokenValue(rawToken);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> list(UUID userId, UUID currentSessionId) {
        return repository.findByUserIdAndRevokedFalse(userId).stream()
                .sorted(Comparator.comparing(SessionService::activityTime).reversed())
                .map(s -> toResponse(s, s.getId().equals(currentSessionId)))
                .toList();
    }

    @Transactional
    public void revoke(UUID userId, UUID sessionId) {
        if (repository.deleteByIdAndUserId(sessionId, userId) == 0) {
            throw new ResourceNotFoundException("Session not found");
        }
    }

    /** Signs out every device except the caller's own. Returns how many were signed out. */
    @Transactional
    public int revokeOthers(UUID userId, UUID currentSessionId) {
        if (currentSessionId == null) {
            throw new BadRequestException("error.session.currentUnknown");
        }
        return repository.deleteByUserIdExcept(userId, currentSessionId);
    }

    /** Ends every session for a user — password reset, account disabled, role or login change. */
    @Transactional
    public void revokeAll(UUID userId) {
        if (userId != null) {
            repository.deleteByUserId(userId);
        }
    }

    private void evictOldest(UUID userId, UUID keepId) {
        if (maxSessionsPerUser <= 0) return;
        List<RefreshToken> all = repository.findByUserIdAndRevokedFalse(userId);
        if (all.size() <= maxSessionsPerUser) return;
        List<UUID> stale = all.stream()
                .filter(s -> !s.getId().equals(keepId))
                .sorted(Comparator.comparing(SessionService::activityTime))
                .limit(all.size() - maxSessionsPerUser)
                .map(RefreshToken::getId)
                .toList();
        if (!stale.isEmpty()) {
            repository.deleteByIds(stale);
            log.info("Signed out {} least-recently-used session(s) for user {} (limit {})",
                    stale.size(), userId, maxSessionsPerUser);
        }
    }

    private static Instant activityTime(RefreshToken s) {
        if (s.getLastUsedAt() != null) return s.getLastUsedAt();
        return s.getCreatedAt() != null ? s.getCreatedAt() : Instant.EPOCH;
    }

    private static SessionResponse toResponse(RefreshToken s, boolean current) {
        String location = s.getLastLocation() != null ? s.getLastLocation() : s.getLoginLocation();
        return SessionResponse.builder()
                .id(s.getId())
                .deviceLabel(s.getDeviceLabel() != null ? s.getDeviceLabel() : "Unknown device")
                .deviceType(s.getDeviceType())
                .browser(s.getBrowser())
                .os(s.getOs())
                .portal(s.getPortal())
                .signedInAt(s.getCreatedAt())
                .lastActiveAt(activityTime(s))
                .loginIp(s.getLoginIp())
                .loginLocation(s.getLoginLocation())
                .lastIp(s.getLastIp())
                .lastLocation(location)
                .current(current)
                .build();
    }

    private static void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
