package com.salonplatform.security;

import com.salonplatform.domain.enums.UserRole;
import com.salonplatform.exception.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static UserPrincipal currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new ForbiddenException("Not authenticated");
        }
        return principal;
    }

    public static UUID requireTenantId() {
        UserPrincipal user = currentUser();
        if (user.getTenantId() == null && user.getRole() != UserRole.PLATFORM_SUPER_ADMIN) {
            throw new ForbiddenException("Tenant context required");
        }
        return user.getTenantId();
    }

    public static void assertBranchAccess(UUID branchId) {
        UserPrincipal user = currentUser();
        if (user.getRole() == UserRole.PLATFORM_SUPER_ADMIN || user.getRole() == UserRole.BRAND_ADMIN) {
            return;
        }
        if (user.getBranchId() == null || !user.getBranchId().equals(branchId)) {
            throw new ForbiddenException("Access denied for this branch");
        }
    }

    public static void assertBrandAdminOrAbove() {
        UserRole role = currentUser().getRole();
        if (role != UserRole.BRAND_ADMIN && role != UserRole.PLATFORM_SUPER_ADMIN) {
            throw new ForbiddenException("Brand admin access required");
        }
    }

    public static void assertPlatformAdmin() {
        if (currentUser().getRole() != UserRole.PLATFORM_SUPER_ADMIN) {
            throw new ForbiddenException("Platform admin access required");
        }
    }
}
