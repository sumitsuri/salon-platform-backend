package com.salonplatform.service.scan;

import java.util.Locale;
import java.util.regex.Pattern;

/** Restricts scan recommendations to face vs hair/scalp services. */
public final class ScanCatalogFilter {

    public enum Domain {
        FACE,
        SCALP
    }

    private static final Pattern FACE_BLOCK = Pattern.compile(
            "(?i)(pedicure|manicure|\\bnail\\b|haircut|hair cut|hair colour|hair color|hair spa|\\bscalp\\b"
                    + "|\\bcolour\\b|\\bcolor\\b|keratin|rebond|smoothen|straightening|highlights|balayage"
                    + "|blow dry|kids cut|foot spa|leg wax|body massage|bridal makeup|makeup pkg)");

    private static final Pattern FACE_ALLOW = Pattern.compile(
            "(?i)(facial|\\bface\\b|\\bskin\\b|peel|de-?tan|cleanup|clean-up|vlcc|hydra facial|bleach"
                    + "|anti-?aging|glow facial|fruit facial|lotus facial|diamond facial|gold facial|acne|blackhead|oxygen facial|o3 facial)");

    private static final Pattern SCALP_BLOCK = Pattern.compile(
            "(?i)(pedicure|manicure|\\bnail\\b|fruit facial|lotus facial|vlcc.*facial|\\bfacial\\b|face peel"
                    + "|face cleanup|foot spa|leg wax|body massage|bridal makeup|makeup pkg|eyebrow|lash extension)");

    private static final Pattern SCALP_ALLOW = Pattern.compile(
            "(?i)(\\bhair\\b|\\bscalp\\b|dandruff|keratin|botox|protein|hair spa|scalp spa|tonic|fall treatment"
                    + "|\\bcolour\\b|\\bcolor\\b|highlight|balayage|smoothen|rebond|olaplex|bond repair|smoothing"
                    + "|head massage|hair treatment|hair mask|deep conditioning|gloss treatment|smoothening)");

    private ScanCatalogFilter() {}

    public static boolean isEligible(Domain domain, String serviceName, String categoryPath) {
        if (serviceName == null || serviceName.isBlank()) {
            return false;
        }
        String combined = (serviceName + " " + nullToEmpty(categoryPath)).trim();

        if (domain == Domain.FACE) {
            if (FACE_BLOCK.matcher(combined).find()) {
                return false;
            }
            if (categoryIndicatesFace(categoryPath)) {
                return true;
            }
            return FACE_ALLOW.matcher(serviceName).find();
        }

        if (SCALP_BLOCK.matcher(combined).find()) {
            return false;
        }
        if (categoryIndicatesScalp(categoryPath)) {
            return true;
        }
        return SCALP_ALLOW.matcher(serviceName).find();
    }

    private static boolean categoryIndicatesFace(String categoryPath) {
        String c = nullToEmpty(categoryPath).toLowerCase(Locale.ROOT);
        return c.contains("facial") || c.contains("skin") || c.contains("face");
    }

    private static boolean categoryIndicatesScalp(String categoryPath) {
        String c = nullToEmpty(categoryPath).toLowerCase(Locale.ROOT);
        return c.contains("hair") || c.contains("colour") || c.contains("color") || c.contains("scalp");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
