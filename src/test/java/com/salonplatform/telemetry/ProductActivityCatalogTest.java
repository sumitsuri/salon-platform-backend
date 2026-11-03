package com.salonplatform.telemetry;

import com.salonplatform.domain.enums.UserRole;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductActivityCatalogTest {

    @Test
    void portalFollowsRoleNotTheSharedLoginForm() {
        assertEquals("admin", ProductActivityCatalog.portalFor(UserRole.BRAND_ADMIN));
        assertEquals("manager", ProductActivityCatalog.portalFor(UserRole.SALON_MANAGER));
        assertEquals("manager", ProductActivityCatalog.portalFor(UserRole.BRANCH_MANAGER));
        assertEquals("employee", ProductActivityCatalog.portalFor(UserRole.SALON_STAFF));
        assertEquals("platform", ProductActivityCatalog.portalFor(UserRole.PLATFORM_SUPER_ADMIN));
    }

    @Test
    void billingActionsStayBillingEvenWhenThePageDoesNotChange() {
        UUID visit = UUID.randomUUID();
        assertEquals("manager.billing", classify(UserRole.SALON_MANAGER, "POST",
                "/api/v1/bookings/" + visit + "/payments"));
        assertEquals("manager.billing", classify(UserRole.SALON_MANAGER, "PUT",
                "/api/v1/bookings/" + visit + "/lines"));
        assertEquals("manager.billing", classify(UserRole.SALON_MANAGER, "GET",
                "/api/v1/bookings/" + visit + "/bill-preview"));
        assertEquals("admin.billing", classify(UserRole.BRAND_ADMIN, "PUT",
                "/api/v1/bookings/" + visit + "/admin/bill"));
        assertEquals("admin.billing", classify(UserRole.BRAND_ADMIN, "GET", "/api/v1/invoices"));
    }

    @Test
    void postLoginWorkIsCategorizedPerApp() {
        assertEquals("manager.schedule", classify(UserRole.BRANCH_MANAGER, "GET", "/api/v1/bookings"));
        assertEquals("admin.bookings", classify(UserRole.BRAND_ADMIN, "GET", "/api/v1/bookings"));
        assertEquals("manager.walk_in", classify(UserRole.SALON_MANAGER, "POST", "/api/v1/bookings"));
        assertEquals("employee.attendance", classify(UserRole.SALON_STAFF, "POST",
                "/api/v1/staff-portal/attendance/punch"));
        assertEquals("employee.sales", classify(UserRole.SALON_STAFF, "GET", "/api/v1/staff-portal/sales/insights"));
        assertEquals("employee.leaves", classify(UserRole.SALON_STAFF, "POST", "/api/v1/staff-portal/leaves"));
        assertEquals("admin.finance", classify(UserRole.BRAND_ADMIN, "GET", "/api/v1/analytics/pl"));
        assertEquals("manager.customers", classify(UserRole.SALON_MANAGER, "GET", "/api/v1/customers/search"));
        assertEquals("manager.inventory", classify(UserRole.SALON_MANAGER, "POST", "/api/v1/inventory/movements"));
    }

    @Test
    void authTelemetryAndPlatformConsoleAreNotProductUsage() {
        assertTrue(ProductActivityCatalog.classify(UserRole.SALON_MANAGER, "POST", "/api/v1/auth/login").isEmpty());
        assertTrue(ProductActivityCatalog.classify(UserRole.SALON_MANAGER, "POST", "/api/v1/auth/logout").isEmpty());
        assertTrue(ProductActivityCatalog.classify(UserRole.SALON_MANAGER, "POST", "/api/v1/auth/refresh").isEmpty());
        assertTrue(ProductActivityCatalog.classify(UserRole.SALON_MANAGER, "POST", "/api/v1/telemetry/events").isEmpty());
        assertTrue(ProductActivityCatalog.classify(UserRole.PLATFORM_SUPER_ADMIN, "GET", "/api/v1/bookings").isEmpty());
        assertTrue(ProductActivityCatalog.classify(UserRole.BRAND_ADMIN, "GET", "/api/v1/platform/usage/portfolio").isEmpty());
    }

    @Test
    void backfillIdIsStable() {
        UUID id = UUID.randomUUID();
        assertEquals(
                ProductActivityCatalog.backfillEventId("payment", id, null),
                ProductActivityCatalog.backfillEventId("payment", id, null));
    }

    private static String classify(UserRole role, String method, String path) {
        return ProductActivityCatalog.classify(role, method, path).orElseThrow().featureKey();
    }
}
