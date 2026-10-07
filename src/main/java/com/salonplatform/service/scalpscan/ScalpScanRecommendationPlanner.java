package com.salonplatform.service.scalpscan;

import com.salonplatform.dto.scalpscan.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class ScalpScanRecommendationPlanner {

    public record CatalogServiceRef(UUID branchServiceId, String name) {}

    private static final String DISCLAIMER =
            "Consultation aid only — not medical diagnosis. Stylist should confirm concerns in chair. "
                    + "Refer itching, bleeding, or sudden hair loss to a dermatologist or trichologist.";

    public ScalpScanReportDto buildReport(
            ScalpScanMetricsDto metrics,
            List<ScalpScanConcernDto> concerns,
            List<CatalogServiceRef> branchServices) {
        List<String> codes = concerns.stream()
                .sorted(Comparator.comparingInt(ScalpScanConcernDto::getScore).reversed())
                .map(ScalpScanConcernDto::getCode)
                .toList();

        List<ScalpScanRoutineStepDto> routine = buildRoutine(codes);
        List<ScalpScanServiceSuggestionDto> services = matchServices(codes, branchServices);

        return ScalpScanReportDto.builder()
                .metrics(metrics)
                .concerns(concerns)
                .routineSteps(routine)
                .inSalonServices(services)
                .disclaimer(DISCLAIMER)
                .build();
    }

    public void trimForConfidence(ScalpScanReportDto report, String confidence) {
        if (report == null || !"LOW".equals(confidence)) {
            return;
        }
        if (report.getConcerns() != null && report.getConcerns().size() > 2) {
            report.setConcerns(report.getConcerns().subList(0, 2));
        }
        if (report.getInSalonServices() != null && report.getInSalonServices().size() > 2) {
            report.setInSalonServices(report.getInSalonServices().subList(0, 2));
        }
        if (report.getRoutineSteps() != null && report.getRoutineSteps().size() > 4) {
            report.setRoutineSteps(report.getRoutineSteps().subList(0, 4));
        }
    }

    public List<ScalpScanConcernDto> scoreConcerns(
            double avgRedness,
            double avgTexture,
            double avgBrightness,
            double avgUv,
            double avgEdges,
            Set<String> staffConfirmed) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("DANDRUFF", clampScore(avgTexture * 120 + avgUv * 200));
        scores.put("OILY_SCALP", clampScore((0.65 - avgBrightness) * 140 + avgEdges * 40));
        scores.put("DRY_SCALP", clampScore((avgBrightness - 0.55) * 120 + avgTexture * 30));
        scores.put("SCALP_IRRITATION", clampScore(avgRedness * 180));
        scores.put("HAIR_THINNING", clampScore((1 - avgEdges) * 100 + avgBrightness * 20));
        scores.put("HAIR_BREAKAGE", clampScore(avgTexture * 70 + avgEdges * 35));
        scores.put("PRODUCT_BUILDUP", clampScore((0.6 - avgBrightness) * 80 + avgTexture * 25));

        for (String code : staffConfirmed) {
            scores.merge(code.toUpperCase(Locale.ROOT), 55, Integer::sum);
        }

        return scores.entrySet().stream()
                .filter(e -> e.getValue() >= 35)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> ScalpScanConcernDto.builder()
                        .code(e.getKey())
                        .label(labelFor(e.getKey()))
                        .severity(severityFor(e.getValue()))
                        .score(Math.min(100, e.getValue()))
                        .insight(insightFor(e.getKey()))
                        .build())
                .limit(5)
                .toList();
    }

    public ScalpScanMetricsDto aggregateMetrics(
            double avgRedness,
            double avgTexture,
            double avgBrightness,
            double avgUv,
            double avgEdges,
            List<ScalpScanConcernDto> concerns) {
        int irritation = clampScore(avgRedness * 160);
        int oiliness = clampScore((0.62 - avgBrightness) * 130);
        int hydration = clampScore((avgBrightness - 0.42) * 110);
        int density = clampScore(avgEdges * 220);
        int penalty = concerns.stream().mapToInt(ScalpScanConcernDto::getScore).sum() / Math.max(1, concerns.size());
        int health = Math.max(25, Math.min(98, 92 - penalty / 3 + (hydration / 10) - (irritation / 15)));

        return ScalpScanMetricsDto.builder()
                .scalpHealthScore(health)
                .oilinessIndex(oiliness)
                .hydrationIndex(hydration)
                .densityIndex(density)
                .irritationIndex(irritation)
                .build();
    }

    private List<ScalpScanRoutineStepDto> buildRoutine(List<String> concernCodes) {
        boolean dandruff = concernCodes.contains("DANDRUFF");
        boolean oily = concernCodes.contains("OILY_SCALP");
        boolean dry = concernCodes.contains("DRY_SCALP");
        boolean thinning = concernCodes.contains("HAIR_THINNING") || concernCodes.contains("HAIR_BREAKAGE");
        boolean irritation = concernCodes.contains("SCALP_IRRITATION");

        List<ScalpScanRoutineStepDto> steps = new ArrayList<>();
        steps.add(step(1, "PRECLEANSE", "Scalp pre-cleanse",
                oily || concernCodes.contains("PRODUCT_BUILDUP")
                        ? "Weekly exfoliating scalp scrub or pre-shampoo clay to lift buildup before cleansing."
                        : "Light pre-cleanse on scalp only when guest uses heavy styling products."));
        steps.add(step(2, "CLEANSE", "Balanced cleanse",
                dandruff
                        ? "Anti-dandruff or pyrithione-zinc shampoo — massage 2 minutes, rinse thoroughly."
                        : oily
                                ? "Gentle clarifying shampoo focused on roots; avoid over-washing lengths."
                                : dry
                                        ? "Sulphate-free hydrating shampoo; lukewarm water only."
                                        : "Gentle daily shampoo matched to scalp type."));
        steps.add(step(3, "TREAT", "Targeted scalp treatment",
                dandruff
                        ? "Leave-on anti-dandruff serum or scalp tonic on affected zones."
                        : irritation
                                ? "Soothing scalp serum with niacinamide or aloe; avoid friction."
                                : thinning
                                        ? "Fortifying scalp serum with peptides or aminexil on hairline and parting."
                                        : "Weekly scalp mask for hydration or oil control as needed."));
        steps.add(step(4, "REGENERATE", "Condition & repair lengths",
                thinning || concernCodes.contains("HAIR_BREAKAGE")
                        ? "Bond-repair or protein-moderate mask on mid-lengths; avoid heavy roots."
                        : "Conditioner or mask on lengths; keep scalp free of heavy silicones if oily."));
        steps.add(step(5, "TEXTURIZE", "Protect & finish",
                "Heat protectant before styling; recommend satin pillowcase and wide-tooth detangling."));
        return steps;
    }

    private List<ScalpScanServiceSuggestionDto> matchServices(List<String> codes, List<CatalogServiceRef> branchServices) {
        if (branchServices == null || branchServices.isEmpty()) {
            return defaultServiceSuggestions(codes);
        }
        List<String> keywords = keywordsFor(codes);
        List<ScalpScanServiceSuggestionDto> matched = branchServices.stream()
                .filter(bs -> {
                    String hay = bs.name().toLowerCase(Locale.ROOT);
                    return keywords.stream().anyMatch(hay::contains);
                })
                .limit(4)
                .map(bs -> ScalpScanServiceSuggestionDto.builder()
                        .branchServiceId(bs.branchServiceId())
                        .name(bs.name())
                        .reason("Matches detected scalp or hair concerns from this scan.")
                        .build())
                .collect(Collectors.toList());
        if (matched.isEmpty()) {
            return defaultServiceSuggestions(codes);
        }
        return matched;
    }

    private List<ScalpScanServiceSuggestionDto> defaultServiceSuggestions(List<String> codes) {
        List<ScalpScanServiceSuggestionDto> list = new ArrayList<>();
        if (codes.contains("DANDRUFF") || codes.contains("SCALP_IRRITATION")) {
            list.add(suggestion(null, "Deep cleansing scalp spa", "Calms flaking and restores scalp comfort in one visit."));
        }
        if (codes.contains("HAIR_BREAKAGE") || codes.contains("DRY_SCALP")) {
            list.add(suggestion(null, "Moisture repair hair spa", "Rebuilds lipids and softness on damaged lengths."));
        }
        if (codes.contains("OILY_SCALP") || codes.contains("PRODUCT_BUILDUP")) {
            list.add(suggestion(null, "Scalp detox treatment", "Removes buildup and balances sebum at the roots."));
        }
        if (codes.contains("HAIR_THINNING")) {
            list.add(suggestion(null, "Fortifying scalp ritual", "Focused massage and concentrated actives on hairline and crown."));
        }
        if (list.isEmpty()) {
            list.add(suggestion(null, "Signature hair spa", "Maintains scalp health and shine between concerns."));
        }
        return list.stream().limit(4).toList();
    }

    private static List<String> keywordsFor(List<String> codes) {
        Set<String> k = new LinkedHashSet<>();
        for (String code : codes) {
            switch (code) {
                case "DANDRUFF" -> k.addAll(List.of("dandruff", "scalp", "anti-dandruff", "flake"));
                case "OILY_SCALP", "PRODUCT_BUILDUP" -> k.addAll(List.of("scalp", "detox", "cleanse", "oily", "clarif"));
                case "DRY_SCALP", "HAIR_BREAKAGE" -> k.addAll(List.of("spa", "moisture", "repair", "hydr", "mask"));
                case "SCALP_IRRITATION" -> k.addAll(List.of("scalp", "sooth", "spa", "sensitive"));
                case "HAIR_THINNING" -> k.addAll(List.of("fall", "thin", "fortif", "scalp", "hair"));
                default -> k.add("scalp");
            }
        }
        if (k.isEmpty()) {
            k.add("hair");
            k.add("scalp");
        }
        return new ArrayList<>(k);
    }

    private static ScalpScanRoutineStepDto step(int n, String phase, String title, String description) {
        return ScalpScanRoutineStepDto.builder()
                .step(n)
                .phase(phase)
                .title(title)
                .description(description)
                .build();
    }

    private static ScalpScanServiceSuggestionDto suggestion(UUID id, String name, String reason) {
        return ScalpScanServiceSuggestionDto.builder()
                .branchServiceId(id)
                .name(name)
                .reason(reason)
                .build();
    }

    private static int clampScore(double raw) {
        return Math.max(0, Math.min(100, (int) Math.round(raw)));
    }

    private static String severityFor(int score) {
        if (score >= 70) return "HIGH";
        if (score >= 50) return "MEDIUM";
        return "LOW";
    }

    private static String labelFor(String code) {
        return switch (code) {
            case "DANDRUFF" -> "Dandruff & flaking";
            case "OILY_SCALP" -> "Oily scalp";
            case "DRY_SCALP" -> "Dry scalp";
            case "SCALP_IRRITATION" -> "Scalp sensitivity";
            case "HAIR_THINNING" -> "Thinning / low density";
            case "HAIR_BREAKAGE" -> "Weak or brittle hair";
            case "PRODUCT_BUILDUP" -> "Product buildup";
            default -> code;
        };
    }

    private static String insightFor(String code) {
        return switch (code) {
            case "DANDRUFF" -> "Elevated surface texture or UV-reactive specks suggest flaking or microbial activity.";
            case "OILY_SCALP" -> "Lower surface brightness at roots may indicate excess sebum.";
            case "DRY_SCALP" -> "High brightness with low hydration index suggests dry or tight scalp.";
            case "SCALP_IRRITATION" -> "Cross-polarized view shows elevated redness — check for sensitivity.";
            case "HAIR_THINNING" -> "Lower edge density at parting may correlate with visible thinning.";
            case "HAIR_BREAKAGE" -> "Uneven texture along shafts can indicate breakage or porosity damage.";
            case "PRODUCT_BUILDUP" -> "Dull roots with residue pattern — clarify before treatment.";
            default -> "Review capture quality and confirm with the guest.";
        };
    }
}
