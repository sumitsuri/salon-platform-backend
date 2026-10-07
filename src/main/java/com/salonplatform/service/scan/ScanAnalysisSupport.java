package com.salonplatform.service.scan;

import com.salonplatform.dto.scan.ScanAnalysisMetaDto;
import com.salonplatform.exception.BadRequestException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ScanAnalysisSupport {

    private ScanAnalysisSupport() {}

    public static void assertPhotoQuality(Set<String> staffConfirmed, List<ScanCaptureQuality> qualities) {
        boolean allLow = qualities.stream().allMatch(q -> "LOW".equals(q.level()));
        boolean anyBad = qualities.stream().anyMatch(q -> !q.acceptable());
        if (staffConfirmed.isEmpty() && (allLow || anyBad)) {
            throw new BadRequestException(
                    "Photo quality is too low for auto-analysis. Retake with even light and fill the frame, "
                            + "or select concerns you see in chair and analyze again.");
        }
    }

    public static ScanAnalysisMetaDto buildMeta(
            List<ScanCaptureQuality> qualities,
            boolean staffInputUsed,
            boolean llmUsed) {
        Set<String> issues = new LinkedHashSet<>();
        String worst = "HIGH";
        boolean anyUnacceptable = false;
        for (ScanCaptureQuality q : qualities) {
            issues.addAll(q.issues());
            if (rank(q.level()) < rank(worst)) {
                worst = q.level();
            }
            if (!q.acceptable()) {
                anyUnacceptable = true;
            }
        }
        String confidence = worst;
        if (!staffInputUsed && rank(confidence) > rank("MEDIUM")) {
            confidence = "MEDIUM";
        }
        if (!staffInputUsed && anyUnacceptable) {
            confidence = "LOW";
        }
        if (staffInputUsed && rank(confidence) > rank("LOW")) {
            confidence = downgrade(confidence);
        }

        String retakeHint = null;
        if (rank(confidence) >= rank("MEDIUM")) {
            retakeHint = "Use even indoor light, fill the frame with scalp/skin, and confirm concerns you see in chair.";
        }
        if (anyUnacceptable) {
            retakeHint = "Retake photos: " + String.join("; ", issues);
        }

        return ScanAnalysisMetaDto.builder()
                .confidence(confidence)
                .method(llmUsed ? "LLM_ASSISTED" : "HEURISTIC")
                .qualityIssues(new ArrayList<>(issues))
                .retakeHint(retakeHint)
                .staffInputUsed(staffInputUsed)
                .build();
    }

    private static int rank(String level) {
        return switch (level) {
            case "LOW" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private static String downgrade(String level) {
        return switch (level) {
            case "HIGH" -> "MEDIUM";
            case "MEDIUM" -> "LOW";
            default -> "LOW";
        };
    }
}
