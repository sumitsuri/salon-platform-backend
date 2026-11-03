package com.salonplatform.telemetry;

import com.salonplatform.domain.enums.UserRole;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Maps an authenticated API call to the usage portal and feature key shown on Customer usage.
 *
 * <p>Screen views alone miss the work that happens without a route change — billing a visit stays on
 * {@code /manager/walk-in}. Login is categorized from the user's role, because brand admins sign in
 * through the shared manager form.
 */
public final class ProductActivityCatalog {

    private static final Pattern UUID_SEGMENT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private ProductActivityCatalog() {}

    public record Classified(String portal, String featureKey) {}

    /** App surface for a role. Platform staff are not customer-usage actors. */
    public static String portalFor(UserRole role) {
        if (role == null) {
            return "manager";
        }
        return switch (role) {
            case SALON_STAFF -> "employee";
            case BRAND_ADMIN -> "admin";
            case PLATFORM_SUPER_ADMIN, SALES_EXECUTIVE -> "platform";
            default -> "manager";
        };
    }

    public static boolean isCustomerUsageRole(UserRole role) {
        return role == UserRole.SALON_STAFF
                || role == UserRole.BRAND_ADMIN
                || role == UserRole.BRANCH_MANAGER
                || role == UserRole.SALON_MANAGER;
    }

    /**
     * @return empty when this request must not be stored (auth, telemetry, platform console, anonymous)
     */
    public static Optional<Classified> classify(UserRole role, String method, String requestUri) {
        if (!isCustomerUsageRole(role)) {
            return Optional.empty();
        }
        String path = normalize(requestUri);
        if (skip(path)) {
            return Optional.empty();
        }
        String portal = portalFor(role);
        String suffix = featureSuffix(portal, method, path);
        if (suffix == null || suffix.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Classified(portal, portal + "." + suffix));
    }

    public static boolean isMutation(String method) {
        if (method == null) {
            return false;
        }
        return switch (method.toUpperCase(Locale.ROOT)) {
            case "POST", "PUT", "PATCH", "DELETE" -> true;
            default -> false;
        };
    }

    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String path = raw.trim();
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        String[] parts = path.split("/");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            out.append('/');
            if (UUID_SEGMENT.matcher(part).matches() || part.chars().allMatch(Character::isDigit)) {
                out.append("{id}");
            } else {
                out.append(part);
            }
        }
        return out.toString();
    }

    private static boolean skip(String path) {
        if (path.isEmpty() || !path.startsWith("/api/v1/")) {
            return true;
        }
        return path.startsWith("/api/v1/auth")
                || path.startsWith("/api/v1/telemetry")
                || path.startsWith("/api/v1/public")
                || path.startsWith("/api/v1/meta")
                || path.startsWith("/api/v1/internal")
                || path.startsWith("/api/v1/platform")
                || path.startsWith("/api/v1/api-docs")
                || path.startsWith("/swagger-ui");
    }

    private static String featureSuffix(String portal, String method, String path) {
        if (path.startsWith("/api/v1/bookings")) {
            return bookingFeature(portal, method, path);
        }
        if (path.startsWith("/api/v1/invoices") || path.startsWith("/api/v1/scratch-cards")) {
            return "billing";
        }
        if (path.startsWith("/api/v1/staff-portal")) {
            return staffPortalFeature(path);
        }
        if (path.startsWith("/api/v1/staff")) {
            if (path.contains("/goals")) {
                return "goals";
            }
            if (path.contains("/reviews")) {
                return "reviews";
            }
            return "employees";
        }
        if (path.startsWith("/api/v1/analytics")) {
            return analyticsFeature(path);
        }
        if (path.startsWith("/api/v1/branches")) {
            return path.contains("/performance") ? "insights" : "branches";
        }
        if (path.startsWith("/api/v1/users/me")) {
            return "profile";
        }
        if (path.startsWith("/api/v1/users")) {
            return "employees";
        }
        if (path.startsWith("/api/v1/customers")) {
            return "customers";
        }
        if (path.startsWith("/api/v1/memberships")) {
            return "memberships";
        }
        if (path.startsWith("/api/v1/packages")) {
            return "packages";
        }
        if (path.startsWith("/api/v1/attendance")) {
            return "attendance";
        }
        if (path.startsWith("/api/v1/inventory")) {
            return "inventory";
        }
        if (path.startsWith("/api/v1/expenditures")) {
            return "expenditure";
        }
        if (path.startsWith("/api/v1/catalog")) {
            return "services";
        }
        if (path.startsWith("/api/v1/leave")) {
            return "leaves";
        }
        if (path.startsWith("/api/v1/salary-advances")) {
            return "advance";
        }
        if (path.startsWith("/api/v1/scalp-scans")) {
            return "scalp_scan";
        }
        if (path.startsWith("/api/v1/face-scans")) {
            return "face_scan";
        }
        if (path.startsWith("/api/v1/campaigns")) {
            return "campaigns";
        }
        if (path.startsWith("/api/v1/whatsapp-templates")) {
            return "whatsapp_templates";
        }
        if (path.startsWith("/api/v1/promotions")) {
            return "promotions";
        }
        if (path.startsWith("/api/v1/scratch-campaigns")) {
            return "scratch_cards";
        }
        if (path.startsWith("/api/v1/enquiries")) {
            return "leads";
        }
        if (path.startsWith("/api/v1/tenant") || path.startsWith("/api/v1/account")) {
            return "settings";
        }
        String rest = path.substring("/api/v1/".length());
        String head = rest.split("/")[0];
        if (head.isBlank() || "{id}".equals(head)) {
            return null;
        }
        return head.replace('-', '_');
    }

    private static String bookingFeature(String portal, String method, String path) {
        if (path.contains("/payments")
                || path.contains("/bill-preview")
                || path.contains("/bill-discount")
                || path.contains("/ready-for-billing")
                || path.contains("/lines")
                || path.contains("/promotions")
                || path.contains("/pending-membership")
                || path.contains("/pending-package")
                || path.contains("/admin/bill")) {
            return "billing";
        }
        if ("/api/v1/bookings".equals(path)) {
            if (isMutation(method)) {
                return "walk_in";
            }
            return "admin".equals(portal) ? "bookings" : "schedule";
        }
        if (path.contains("/reschedule") || path.contains("/check-in")) {
            return "admin".equals(portal) ? "bookings" : "schedule";
        }
        return "walk_in";
    }

    private static String staffPortalFeature(String path) {
        if (path.contains("/attendance")) {
            return "attendance";
        }
        if (path.contains("/leaves")) {
            return "leaves";
        }
        if (path.contains("/advances")) {
            return "advance";
        }
        if (path.contains("/goals")) {
            return "goals";
        }
        if (path.contains("/growth")) {
            return "growth";
        }
        if (path.contains("/sales")) {
            return "sales";
        }
        if (path.contains("/reviews")) {
            return "reviews";
        }
        return "profile";
    }

    private static String analyticsFeature(String path) {
        if (path.startsWith("/api/v1/analytics/pl")) {
            return "finance";
        }
        if (path.startsWith("/api/v1/analytics/demand")) {
            return "busy_hours";
        }
        if (path.contains("/local-spotlight")) {
            return "local_spotlight";
        }
        if (path.contains("/benchmark")) {
            return "market_pulse";
        }
        if (path.contains("/attendance")) {
            return "attendance";
        }
        if (path.contains("/dashboard")) {
            return "home";
        }
        return "insights";
    }

    /** Stable id so a historical backfill can run on every boot without duplicating rows. */
    public static UUID backfillEventId(String source, UUID entityId, String part) {
        String key = "usage-backfill:" + source + ":" + entityId + (part == null || part.isBlank() ? "" : ":" + part);
        return UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
