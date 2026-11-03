package com.salonplatform.telemetry;

import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.enums.UserRole;
import com.salonplatform.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ProductActivityFilterTest {

    private final ProductTelemetryIngestService ingest = mock(ProductTelemetryIngestService.class);
    private final ProductActivityFilter filter = new ProductActivityFilter(ingest);

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void paymentIsBillingAndARepeatInsideTheWindowIsCollapsed() throws Exception {
        UserPrincipal manager = principal(UserRole.SALON_MANAGER);
        signIn(manager);
        String path = "/api/v1/bookings/" + UUID.randomUUID() + "/payments";

        filter.doFilter(request("POST", path), ok(), (req, res) -> {});
        filter.doFilter(request("POST", path), ok(), (req, res) -> {});

        verify(ingest, times(1)).recordActivity(
                eq(manager), eq("manager"), eq("manager.billing"), eq("/api/v1/bookings/{id}/payments"), eq("POST"));
    }

    @Test
    void previewPollDoesNotHideThePayment() throws Exception {
        UserPrincipal manager = principal(UserRole.SALON_MANAGER);
        signIn(manager);
        UUID visit = UUID.randomUUID();

        filter.doFilter(request("GET", "/api/v1/bookings/" + visit + "/bill-preview"), ok(), (req, res) -> {});
        filter.doFilter(request("POST", "/api/v1/bookings/" + visit + "/payments"), ok(), (req, res) -> {});

        verify(ingest).recordActivity(
                eq(manager), eq("manager"), eq("manager.billing"), eq("/api/v1/bookings/{id}/bill-preview"), eq("GET"));
        verify(ingest).recordActivity(
                eq(manager), eq("manager"), eq("manager.billing"), eq("/api/v1/bookings/{id}/payments"), eq("POST"));
    }

    @Test
    void employeePunchAndAdminBillUseTheirOwnApps() throws Exception {
        UserPrincipal employee = principal(UserRole.SALON_STAFF);
        signIn(employee);
        filter.doFilter(request("POST", "/api/v1/staff-portal/attendance/punch"), ok(), (req, res) -> {});
        verify(ingest).recordActivity(
                eq(employee), eq("employee"), eq("employee.attendance"),
                eq("/api/v1/staff-portal/attendance/punch"), eq("POST"));

        UserPrincipal admin = principal(UserRole.BRAND_ADMIN);
        signIn(admin);
        filter.doFilter(request("GET", "/api/v1/analytics/pl"), ok(), (req, res) -> {});
        verify(ingest).recordActivity(
                eq(admin), eq("admin"), eq("admin.finance"), eq("/api/v1/analytics/pl"), eq("GET"));
    }

    @Test
    void failedAndUnsignedCallsAreIgnored() throws Exception {
        UserPrincipal manager = principal(UserRole.SALON_MANAGER);
        signIn(manager);
        MockHttpServletResponse failed = ok();
        failed.setStatus(500);
        FilterChain chain = (req, res) -> {};
        filter.doFilter(request("POST", "/api/v1/bookings/" + UUID.randomUUID() + "/payments"), failed, chain);

        SecurityContextHolder.clearContext();
        filter.doFilter(request("POST", "/api/v1/bookings"), ok(), chain);

        verify(ingest, never()).recordActivity(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private static MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }

    private static MockHttpServletResponse ok() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        return response;
    }

    private static void signIn(UserPrincipal user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private static UserPrincipal principal(UserRole role) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .branchId(UUID.randomUUID())
                .name("Mango")
                .email(role.name().toLowerCase() + "@mango.test")
                .password("secret")
                .role(role)
                .active(true)
                .build();
        return new UserPrincipal(user);
    }
}
