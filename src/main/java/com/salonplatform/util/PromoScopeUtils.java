package com.salonplatform.util;

import com.salonplatform.domain.enums.ServiceScopeType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class PromoScopeUtils {

    private PromoScopeUtils() {}

    public static String joinIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream().map(UUID::toString).collect(Collectors.joining(","));
    }

    public static List<UUID> parseIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(UUID::fromString)
                .collect(Collectors.toList());
    }

    public static boolean branchAllowed(String branchIdsCsv, UUID branchId) {
        List<UUID> ids = parseIds(branchIdsCsv);
        return ids.isEmpty() || ids.contains(branchId);
    }

    public static boolean serviceEligible(
            ServiceScopeType scope,
            String scopeIdsCsv,
            UUID serviceId,
            UUID categoryId) {
        if (scope == null || scope == ServiceScopeType.ALL) {
            return true;
        }
        Set<UUID> ids = Set.copyOf(parseIds(scopeIdsCsv));
        if (ids.isEmpty()) {
            return true;
        }
        if (scope == ServiceScopeType.CATEGORY) {
            return categoryId != null && ids.contains(categoryId);
        }
        return serviceId != null && ids.contains(serviceId);
    }
}
