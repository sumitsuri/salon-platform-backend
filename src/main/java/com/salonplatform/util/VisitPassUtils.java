package com.salonplatform.util;

import com.salonplatform.domain.entity.Tenant;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;

public final class VisitPassUtils {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** New format: MW-VAR-482915 */
    private static final Pattern PASS_WITH_BRANCH_PATTERN =
            Pattern.compile("^[A-Z0-9]{2,4}-[A-Z0-9]{2,10}-\\d{6}$");
    /** Legacy (pre-branch) format: MW-482915 — still accepted for lookup. */
    private static final Pattern PASS_LEGACY_PATTERN = Pattern.compile("^[A-Z0-9]{2,4}-\\d{6}$");

    public static final String FALLBACK_BRANCH_CODE = "GEN";

    private VisitPassUtils() {}

    /** Derive a short tenant prefix from slug, e.g. mystic-wellness → MW. */
    public static String tenantPrefix(Tenant tenant) {
        if (tenant == null || tenant.getSlug() == null || tenant.getSlug().isBlank()) {
            return "VP";
        }
        String[] parts = tenant.getSlug().trim().toLowerCase(Locale.ROOT).split("-");
        StringBuilder prefix = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                prefix.append(Character.toUpperCase(part.charAt(0)));
            }
            if (prefix.length() >= 3) {
                break;
            }
        }
        if (prefix.length() < 2) {
            String slug = tenant.getSlug().replaceAll("[^a-zA-Z0-9]", "").toUpperCase(Locale.ROOT);
            if (slug.length() >= 2) {
                return slug.substring(0, 2);
            }
            return "VP";
        }
        return prefix.toString();
    }

    /** Normalize branch code from {@link com.salonplatform.domain.entity.Branch#getCode()}. */
    public static String normalizeBranchCode(String branchCode) {
        if (branchCode == null || branchCode.isBlank()) {
            return FALLBACK_BRANCH_CODE;
        }
        String normalized = branchCode.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return normalized.isEmpty() ? FALLBACK_BRANCH_CODE : normalized;
    }

    public static String generateVisitPassId(Tenant tenant, String branchCode) {
        String prefix = tenantPrefix(tenant);
        String branch = normalizeBranchCode(branchCode);
        int n = RANDOM.nextInt(900_000) + 100_000;
        return prefix + "-" + branch + "-" + n;
    }

    public static String generatePublicToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(48);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    /** Normalize user input: trim, uppercase, collapse spaces. */
    public static String normalizeVisitPassId(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static boolean looksLikeVisitPassId(String raw) {
        String normalized = normalizeVisitPassId(raw);
        if (normalized == null) {
            return false;
        }
        return PASS_WITH_BRANCH_PATTERN.matcher(normalized).matches()
                || PASS_LEGACY_PATTERN.matcher(normalized).matches();
    }
}
