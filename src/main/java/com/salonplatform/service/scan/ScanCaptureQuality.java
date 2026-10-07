package com.salonplatform.service.scan;

import java.util.ArrayList;
import java.util.List;

public record ScanCaptureQuality(
        boolean acceptable,
        String level,
        List<String> issues,
        double meanBrightness,
        double sharpness) {

    public static ScanCaptureQuality assess(double meanBrightness, double sharpness) {
        List<String> issues = new ArrayList<>();
        if (meanBrightness < 0.12) {
            issues.add("Photo is too dark");
        } else if (meanBrightness > 0.92) {
            issues.add("Photo is overexposed (washed out)");
        }
        if (sharpness < 0.018) {
            issues.add("Photo looks blurry — hold steady and tap to focus");
        }
        String level;
        if (issues.size() >= 2 || sharpness < 0.012) {
            level = "LOW";
        } else if (!issues.isEmpty() || sharpness < 0.025) {
            level = "MEDIUM";
        } else {
            level = "HIGH";
        }
        boolean acceptable = !issues.contains("Photo looks blurry — hold steady and tap to focus") || sharpness >= 0.012;
        return new ScanCaptureQuality(acceptable, level, issues, meanBrightness, sharpness);
    }
}
