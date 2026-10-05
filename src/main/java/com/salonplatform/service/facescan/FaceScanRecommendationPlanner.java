package com.salonplatform.service.facescan;

import com.salonplatform.dto.facescan.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class FaceScanRecommendationPlanner {

    public record CatalogServiceRef(UUID branchServiceId, String name) {}

    private static final String DISCLAIMER =
            "Face scan supports skincare consultation and treatment planning. It is not medical diagnosis. "
                    + "Refer persistent rash, swelling, or sudden changes to a dermatologist.";

    public FaceScanReportDto buildReport(
            FaceScanMetricsDto metrics,
            List<FaceScanConcernDto> concerns,
            List<CatalogServiceRef> services) {
        List<String> codes = concerns.stream()
                .sorted(Comparator.comparingInt(FaceScanConcernDto::getScore).reversed())
                .map(FaceScanConcernDto::getCode)
                .toList();
        return FaceScanReportDto.builder()
                .metrics(metrics)
                .concerns(concerns)
                .routineSteps(buildRoutine(codes))
                .inSalonServices(matchServices(codes, services))
                .disclaimer(DISCLAIMER)
                .build();
    }

    public List<FaceScanConcernDto> scoreConcerns(
            double avgRedness,
            double avgTexture,
            double avgBrightness,
            double avgUv,
            Set<String> staffConfirmed) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("OILY_SKIN", clamp((0.58 - avgBrightness) * 150 + avgTexture * 25));
        scores.put("DRY_DEHYDRATED", clamp((avgBrightness - 0.5) * 130 + (1 - avgTexture) * 20));
        scores.put("REDNESS_SENSITIVITY", clamp(avgRedness * 200));
        scores.put("UNEVEN_TEXTURE", clamp(avgTexture * 110));
        scores.put("DULLNESS", clamp((avgBrightness - 0.45) * 80 + avgTexture * 15));
        scores.put("ACNE_BLEMISH", clamp(avgTexture * 70 + avgRedness * 90 + avgUv * 120));
        scores.put("COMBINATION_SKIN", clamp(Math.abs(avgBrightness - 0.52) * 60 + avgTexture * 40));

        for (String code : staffConfirmed) {
            scores.merge(code.toUpperCase(Locale.ROOT), 25, Integer::sum);
        }

        return scores.entrySet().stream()
                .filter(e -> e.getValue() >= 35)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> FaceScanConcernDto.builder()
                        .code(e.getKey())
                        .label(labelFor(e.getKey()))
                        .severity(severityFor(e.getValue()))
                        .score(Math.min(100, e.getValue()))
                        .insight(insightFor(e.getKey()))
                        .build())
                .limit(5)
                .toList();
    }

    public FaceScanMetricsDto aggregateMetrics(
            double avgRedness,
            double avgTexture,
            double avgBrightness,
            List<FaceScanConcernDto> concerns) {
        int oiliness = clamp((0.6 - avgBrightness) * 140);
        int hydration = clamp((avgBrightness - 0.38) * 115);
        int texture = clamp(avgTexture * 180);
        int redness = clamp(avgRedness * 170);
        int penalty = concerns.stream().mapToInt(FaceScanConcernDto::getScore).sum() / Math.max(1, concerns.size());
        int health = Math.max(25, Math.min(98, 90 - penalty / 3 + hydration / 12 - redness / 18));

        return FaceScanMetricsDto.builder()
                .skinHealthScore(health)
                .oilinessIndex(oiliness)
                .hydrationIndex(hydration)
                .textureIndex(texture)
                .rednessIndex(redness)
                .build();
    }

    private List<FaceScanRoutineStepDto> buildRoutine(List<String> codes) {
        boolean oily = codes.contains("OILY_SKIN") || codes.contains("ACNE_BLEMISH");
        boolean dry = codes.contains("DRY_DEHYDRATED");
        boolean red = codes.contains("REDNESS_SENSITIVITY");
        boolean texture = codes.contains("UNEVEN_TEXTURE") || codes.contains("DULLNESS");

        List<FaceScanRoutineStepDto> steps = new ArrayList<>();
        steps.add(step(1, "CLEANSE", "Gentle face cleanse",
                oily ? "Gel or foaming cleanser — focus T-zone; lukewarm water." :
                        dry ? "Cream or milk cleanser; avoid stripping surfactants." :
                                "pH-balanced cleanser morning and night."));
        steps.add(step(2, "TREAT", "Targeted serum",
                red ? "Soothing serum with niacinamide or centella; patch-test new actives." :
                        oily ? "Niacinamide or salicylic serum on oily zones." :
                                dry ? "Hyaluronic acid on damp skin before moisturizer." :
                                        "Antioxidant serum (vitamin C) in AM."));
        steps.add(step(3, "CORRECT", "Texture & tone",
                texture ? "2–3× weekly gentle AHA/BHA exfoliant; not on same day as strong facial." :
                        "Weekly enzyme or mild exfoliation if skin feels rough."));
        steps.add(step(4, "MOISTURIZE", "Barrier support",
                dry ? "Rich ceramide moisturizer; facial oil optional at night." :
                        oily ? "Light gel moisturizer; non-comedogenic." :
                                "Layer lightweight moisturizer AM/PM."));
        steps.add(step(5, "PROTECT", "Daily SPF",
                "Broad-spectrum SPF 30+ every morning — essential after brightening or peel facials."));
        return steps;
    }

    private List<FaceScanServiceSuggestionDto> matchServices(List<String> codes, List<CatalogServiceRef> catalog) {
        if (catalog == null || catalog.isEmpty()) {
            return defaults(codes);
        }
        List<String> keywords = keywordsFor(codes);
        List<FaceScanServiceSuggestionDto> matched = catalog.stream()
                .filter(s -> keywords.stream().anyMatch(k -> s.name().toLowerCase(Locale.ROOT).contains(k)))
                .limit(4)
                .map(s -> FaceScanServiceSuggestionDto.builder()
                        .branchServiceId(s.branchServiceId())
                        .name(s.name())
                        .reason("Matched your branch facial menu to detected skin concerns.")
                        .build())
                .collect(Collectors.toList());
        return matched.isEmpty() ? defaults(codes) : matched;
    }

    private List<FaceScanServiceSuggestionDto> defaults(List<String> codes) {
        List<FaceScanServiceSuggestionDto> list = new ArrayList<>();
        if (codes.contains("OILY_SKIN") || codes.contains("ACNE_BLEMISH")) {
            list.add(svc(null, "Deep cleansing facial", "Decongests pores and balances excess sebum."));
        }
        if (codes.contains("DRY_DEHYDRATED")) {
            list.add(svc(null, "Hydrating glow facial", "Restores moisture and soothes tight, flaky skin."));
        }
        if (codes.contains("REDNESS_SENSITIVITY")) {
            list.add(svc(null, "Calming sensitive-skin facial", "Reduces visible redness with gentle actives."));
        }
        if (codes.contains("UNEVEN_TEXTURE") || codes.contains("DULLNESS")) {
            list.add(svc(null, "Brightening / de-tan facial", "Evens tone and improves surface smoothness."));
        }
        if (list.isEmpty()) {
            list.add(svc(null, "Signature facial", "Maintenance treatment for healthy, balanced skin."));
        }
        return list.stream().limit(4).toList();
    }

    private static List<String> keywordsFor(List<String> codes) {
        Set<String> k = new LinkedHashSet<>();
        for (String code : codes) {
            switch (code) {
                case "OILY_SKIN", "ACNE_BLEMISH" -> k.addAll(List.of("facial", "cleanup", "clean", "acne", "oily", "decongest"));
                case "DRY_DEHYDRATED" -> k.addAll(List.of("hydra", "moist", "facial", "glow", "nourish"));
                case "REDNESS_SENSITIVITY" -> k.addAll(List.of("calm", "sooth", "sensitive", "facial", "redness"));
                case "UNEVEN_TEXTURE", "DULLNESS" -> k.addAll(List.of("bright", "detan", "de-tan", "polish", "facial", "glow"));
                case "COMBINATION_SKIN" -> k.addAll(List.of("facial", "skin", "balance"));
                default -> k.add("facial");
            }
        }
        if (k.isEmpty()) k.add("facial");
        return new ArrayList<>(k);
    }

    private static FaceScanRoutineStepDto step(int n, String phase, String title, String description) {
        return FaceScanRoutineStepDto.builder().step(n).phase(phase).title(title).description(description).build();
    }

    private static FaceScanServiceSuggestionDto svc(UUID id, String name, String reason) {
        return FaceScanServiceSuggestionDto.builder().branchServiceId(id).name(name).reason(reason).build();
    }

    private static int clamp(double raw) {
        return Math.max(0, Math.min(100, (int) Math.round(raw)));
    }

    private static String severityFor(int score) {
        if (score >= 70) return "HIGH";
        if (score >= 50) return "MEDIUM";
        return "LOW";
    }

    private static String labelFor(String code) {
        return switch (code) {
            case "OILY_SKIN" -> "Oily skin";
            case "DRY_DEHYDRATED" -> "Dry / dehydrated";
            case "REDNESS_SENSITIVITY" -> "Redness & sensitivity";
            case "UNEVEN_TEXTURE" -> "Uneven texture";
            case "DULLNESS" -> "Dullness";
            case "ACNE_BLEMISH" -> "Blemish-prone areas";
            case "COMBINATION_SKIN" -> "Combination skin";
            default -> code;
        };
    }

    private static String insightFor(String code) {
        return switch (code) {
            case "OILY_SKIN" -> "T-zone shine and lower surface brightness suggest excess sebum.";
            case "DRY_DEHYDRATED" -> "High brightness with low hydration index suggests tight, flaky skin.";
            case "REDNESS_SENSITIVITY" -> "Elevated redness in cross-polarized view — check for sensitivity triggers.";
            case "UNEVEN_TEXTURE" -> "Micro-texture variation may indicate buildup or slow cell turnover.";
            case "DULLNESS" -> "Flat reflectance — brightening and exfoliation may restore glow.";
            case "ACNE_BLEMISH" -> "Localized texture and redness pattern consistent with congested pores.";
            case "COMBINATION_SKIN" -> "Mixed oily and dry signals across zones — treat by area.";
            default -> "Confirm visually under good lighting.";
        };
    }
}
