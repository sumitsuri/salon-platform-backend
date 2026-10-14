package com.salonplatform.service;

import com.salonplatform.domain.entity.RefreshToken;
import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.enums.UserRole;
import com.salonplatform.domain.repository.RefreshTokenRepository;
import com.salonplatform.domain.repository.UserRepository;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.UnauthorizedException;
import com.salonplatform.security.JwtTokenProvider;
import com.salonplatform.session.ClientInfo;
import com.salonplatform.session.SessionLocationUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SessionServiceTest {

    private static final long TWENTY_DAYS_MS = Duration.ofDays(20).toMillis();

    private RefreshTokenRepository repository;
    private UserRepository userRepository;
    private SessionLocationUpdater locationUpdater;
    private SessionService service;

    @BeforeEach
    void setUp() {
        repository = mock(RefreshTokenRepository.class);
        userRepository = mock(UserRepository.class);
        locationUpdater = mock(SessionLocationUpdater.class);
        JwtTokenProvider jwt = mock(JwtTokenProvider.class);
        when(jwt.getRefreshTokenExpiryMs()).thenReturn(TWENTY_DAYS_MS);
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken t = inv.getArgument(0);
            if (t.getId() == null) t.setId(UUID.randomUUID());
            return t;
        });
        service = new SessionService(repository, userRepository, jwt, locationUpdater);
        ReflectionTestUtils.setField(service, "maxSessionsPerUser", 3);
    }

    private User user(UserRole role, boolean active) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setRole(role);
        u.setActive(active);
        return u;
    }

    private RefreshToken session(User u, String token, Instant expiresAt, Instant lastUsed) {
        return RefreshToken.builder().id(UUID.randomUUID()).userId(u.getId()).token(token)
                .expiresAt(expiresAt).lastUsedAt(lastUsed).createdAt(lastUsed).build();
    }

    private void stub(User u, RefreshToken t) {
        when(repository.findByTokenAndRevokedFalse(t.getToken())).thenReturn(Optional.of(t));
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
    }

    @Test
    void ordinaryUserSessionNeverExpiresButPlatformStaffKeepAnIdleLimit() {
        RefreshToken staff = service.open(UUID.randomUUID(), UserRole.SALON_STAFF, "t1",
                new ClientInfo("49.37.1.2", "Mozilla/5.0 (Windows NT 10.0) Chrome/126.0 Safari/537.36", "employee"));
        assertNull(staff.getExpiresAt());
        assertEquals("Chrome on Windows", staff.getDeviceLabel());
        assertEquals("employee", staff.getPortal());
        assertEquals("49.37.1.2", staff.getLoginIp());

        assertNotNull(service.open(UUID.randomUUID(), UserRole.PLATFORM_SUPER_ADMIN, "t2", ClientInfo.unknown()).getExpiresAt());
        assertNotNull(service.open(UUID.randomUUID(), UserRole.SALES_EXECUTIVE, "t3", ClientInfo.unknown()).getExpiresAt());
        assertNull(service.open(UUID.randomUUID(), UserRole.BRAND_ADMIN, "t4", ClientInfo.unknown()).getExpiresAt());
    }

    @Test
    void openingASessionNeverTouchesTheUsersOtherDevices() {
        service.open(UUID.randomUUID(), UserRole.BRAND_ADMIN, "t", ClientInfo.unknown());
        verify(repository, never()).deleteByUserId(any());
    }

    @Test
    void oldestUnusedDeviceIsEvictedOnlyBeyondTheCap() {
        User u = user(UserRole.SALON_MANAGER, true);
        Instant now = Instant.now();
        RefreshToken oldest = session(u, "a", null, now.minus(Duration.ofDays(30)));
        RefreshToken mid = session(u, "b", null, now.minus(Duration.ofDays(5)));
        RefreshToken recent = session(u, "c", null, now.minus(Duration.ofDays(1)));
        when(repository.findByUserIdAndRevokedFalse(u.getId())).thenAnswer(inv -> {
            List<RefreshToken> all = new ArrayList<>(List.of(oldest, mid, recent));
            return all;
        });
        // Three existing + the new one is not in the stubbed list; at exactly the cap nothing is evicted.
        service.open(u.getId(), u.getRole(), "d", ClientInfo.unknown());
        verify(repository, never()).deleteByIds(any());

        RefreshToken extra = session(u, "e", null, now);
        when(repository.findByUserIdAndRevokedFalse(u.getId())).thenReturn(List.of(oldest, mid, recent, extra));
        service.open(u.getId(), u.getRole(), "f", ClientInfo.unknown());
        verify(repository).deleteByIds(List.of(oldest.getId()));
    }

    @Test
    void legacySessionThatAlreadyLapsedIsRevivedForOrdinaryUsers() {
        User u = user(UserRole.SALON_STAFF, true);
        RefreshToken t = session(u, "tok", Instant.now().minus(Duration.ofDays(3)), Instant.now().minus(Duration.ofDays(30)));
        stub(u, t);

        SessionService.Validated v = service.validateAndTouch("tok", new ClientInfo("49.37.1.2", null, null));

        assertNull(v.session().getExpiresAt());
        assertTrue(v.session().getLastUsedAt().isAfter(Instant.now().minusSeconds(5)));
        verify(repository).save(t);
    }

    @Test
    void platformStaffExpireAfterTheIdleWindowAndSlideWhenActive() {
        User admin = user(UserRole.PLATFORM_SUPER_ADMIN, true);
        RefreshToken lapsed = session(admin, "old", Instant.now().minusSeconds(60), Instant.now().minus(Duration.ofDays(25)));
        stub(admin, lapsed);
        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> service.validateAndTouch("old", ClientInfo.unknown()));
        assertEquals("error.refreshToken.expired", ex.getMessage());
        verify(repository).deleteByTokenValue("old");

        RefreshToken live = session(admin, "live", Instant.now().plus(Duration.ofDays(2)), Instant.now().minus(Duration.ofHours(3)));
        stub(admin, live);
        service.validateAndTouch("live", ClientInfo.unknown());
        assertTrue(live.getExpiresAt().isAfter(Instant.now().plus(Duration.ofDays(19))));
    }

    @Test
    void unknownRevokedOrDisabledSessionsAreRejectedWith401Semantics() {
        when(repository.findByTokenAndRevokedFalse("nope")).thenReturn(Optional.empty());
        assertEquals("error.session.revoked",
                assertThrows(UnauthorizedException.class, () -> service.validateAndTouch("nope", null)).getMessage());

        User disabled = user(UserRole.SALON_STAFF, false);
        RefreshToken t = session(disabled, "dis", null, Instant.now());
        stub(disabled, t);
        assertEquals("error.session.accountDisabled",
                assertThrows(UnauthorizedException.class, () -> service.validateAndTouch("dis", null)).getMessage());
        verify(repository).deleteByTokenValue("dis");
    }

    @Test
    void recentSessionIsNotRewrittenOnEveryRefresh() {
        User u = user(UserRole.BRANCH_MANAGER, true);
        RefreshToken t = session(u, "fresh", null, Instant.now().minus(Duration.ofMinutes(10)));
        stub(u, t);
        service.validateAndTouch("fresh", new ClientInfo("49.37.1.2", null, null));
        verify(repository, never()).save(any());
        verify(locationUpdater, never()).resolveForLastSeen(any(), any());
    }

    @Test
    void changedIpTriggersALocationRefresh() {
        User u = user(UserRole.BRANCH_MANAGER, true);
        RefreshToken t = session(u, "moved", null, Instant.now().minus(Duration.ofHours(2)));
        t.setLastIp("49.37.1.2");
        stub(u, t);
        service.validateAndTouch("moved", new ClientInfo("117.1.1.1", null, null));
        assertEquals("117.1.1.1", t.getLastIp());
        verify(locationUpdater).resolveForLastSeen(t.getId(), "117.1.1.1");
    }

    @Test
    void logoutIsIdempotentAndIgnoresEmptyTokens() {
        service.logout(null);
        service.logout("  ");
        verifyNoInteractions(repository);
        service.logout("tok");
        verify(repository).deleteByTokenValue("tok");
    }

    @Test
    void revokeOthersNeedsToKnowTheCurrentDevice() {
        assertThrows(BadRequestException.class, () -> service.revokeOthers(UUID.randomUUID(), null));
        UUID userId = UUID.randomUUID();
        UUID current = UUID.randomUUID();
        when(repository.deleteByUserIdExcept(userId, current)).thenReturn(2);
        assertEquals(2, service.revokeOthers(userId, current));
    }

    @Test
    void listMarksTheCurrentDeviceAndOrdersByRecentActivity() {
        User u = user(UserRole.BRAND_ADMIN, true);
        RefreshToken older = session(u, "a", null, Instant.now().minus(Duration.ofDays(2)));
        RefreshToken newer = session(u, "b", null, Instant.now().minus(Duration.ofMinutes(1)));
        older.setDeviceLabel("Safari on iPhone");
        newer.setDeviceLabel("Chrome on Windows");
        newer.setLoginLocation("Pune, Maharashtra, India");
        when(repository.findByUserIdAndRevokedFalse(u.getId())).thenReturn(List.of(older, newer));

        var rows = service.list(u.getId(), older.getId());

        assertEquals("Chrome on Windows", rows.get(0).getDeviceLabel());
        assertEquals("Pune, Maharashtra, India", rows.get(0).getLastLocation());
        assertFalse(rows.get(0).isCurrent());
        assertTrue(rows.get(1).isCurrent());
    }
}
