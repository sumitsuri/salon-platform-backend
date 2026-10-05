package com.salonplatform.service.facescan;

import com.salonplatform.dto.facescan.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class FaceScanRecommendationPlanner {

    public record CatalogServiceRef(UUID branchServiceId, String name) {}

    private record ScoredCatalogEntry(CatalogServiceRef service, int score, String concernCode, String matchKind) {}

    private record StageTemplate(String stageKey, String titleSuffix, String goal, String rationaleTemplate) {}

    private static final String DISCLAIMER =
            "Face scan supports skincare consultation and treatment planning. It is not medical diagnosis. "
                    + "Refer persistent rash, swelling, or sudden changes to a dermatologist. "
                    + "Plans are generated from your branch menu and scan signals; a stylist should confirm before booking.";

    private static final String APPROACH_NOTE =
            "This program is built from your scan metrics and branch facial menu. "
                    + "Smarter vision models (e.g. OpenAI) can refine plans in a future release.";

    public FaceScanReportDto buildReport(
            FaceScanMetricsDto metrics,
            List<FaceScanConcernDto> concerns,
            List<CatalogServiceRef> services) {
        List<String> codes = concerns.stream()
                .sorted(Comparator.comparingInt(FaceScanConcernDto::getScore).reversed())
                .map(FaceScanConcernDto::getCode)
                .toList();
        List<FaceScanCarePlanPhaseDto> phases = buildCarePlan(metrics, concerns, codes, services);
        List<FaceScanServiceSuggestionDto> flatSalon = phases.stream()
                .map(FaceScanCarePlanPhaseDto::getInSalonVisit)
                .filter(Objects::nonNull)
                .toList();
        List<FaceScanRoutineStepDto> flatRoutine = phases.isEmpty()
                ? buildRoutine(codes)
                : phases.get(phases.size() - 1).getHomeRoutine();

        String primaryLabel = concerns.isEmpty() ? "balanced skin" : concerns.get(0).getLabel().toLowerCase(Locale.ROOT);

        return FaceScanReportDto.builder()
                .metrics(metrics)
                .concerns(concerns)
                .carePlanSummary(FaceScanCarePlanSummaryDto.builder()
                        .durationMonths(phases.size())
                        .headline("Personalized " + phases.size() + "-month skin program")
                        .approachNote(APPROACH_NOTE)
                        .build())
                .carePlanPhases(phases)
                .routineSteps(flatRoutine)
                .inSalonServices(flatSalon)
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

    private List<FaceScanCarePlanPhaseDto> buildCarePlan(
            FaceScanMetricsDto metrics,
            List<FaceScanConcernDto> concerns,
            List<String> codes,
            List<CatalogServiceRef> catalog) {
        String primary = codes.isEmpty() ? "BALANCED" : codes.get(0);
        String secondary = codes.size() > 1 ? codes.get(1) : primary;
        int varietySeed = metrics.getOilinessIndex() * 3 + metrics.getHydrationIndex() * 7
                + metrics.getTextureIndex() * 11 + metrics.getRednessIndex() * 13
                + metrics.getSkinHealthScore();

        List<StageTemplate> templates = stageTemplatesFor(primary, secondary, metrics);
        List<ScoredCatalogEntry> ranked = rankCatalog(catalog, codes, metrics);
        Set<UUID> usedServiceIds = new HashSet<>();
        List<FaceScanCarePlanPhaseDto> phases = new ArrayList<>();

        for (int i = 0; i < templates.size(); i++) {
            StageTemplate template = templates.get(i);
            int month = i + 1;
            String targetConcern = concernForStage(i, primary, secondary, codes);
            ScoredCatalogEntry pick = pickServiceForStage(ranked, targetConcern, template.stageKey(), varietySeed, month, usedServiceIds);
            FaceScanServiceSuggestionDto visit = toVisitSuggestion(pick, targetConcern, metrics, concerns, month, template);
            if (pick != null && pick.service().branchServiceId() != null) {
                usedServiceIds.add(pick.service().branchServiceId());
            }

            phases.add(FaceScanCarePlanPhaseDto.builder()
                    .month(month)
                    .title("Month " + month + " — " + template.titleSuffix())
                    .goal(template.goal())
                    .rationale(fillRationale(template.rationaleTemplate(), metrics, concerns, targetConcern, month))
                    .visitCadence(month == 1 ? "1 in-salon facial + daily home care" : "1 facial every 4 weeks + adjusted home routine")
                    .inSalonVisit(visit)
                    .homeRoutine(homeRoutineForMonth(month, codes, metrics, targetConcern))
                    .build());
        }
        return phases;
    }

    private static String concernForStage(int stageIndex, String primary, String secondary, List<String> codes) {
        return switch (stageIndex) {
            case 0 -> primary;
            case 1 -> secondary;
            default -> codes.size() > 2 ? codes.get(2) : (secondary.equals("BALANCED") ? primary : secondary);
        };
    }

    private List<StageTemplate> stageTemplatesFor(String primary, String secondary, FaceScanMetricsDto metrics) {
        return switch (primary) {
            case "OILY_SKIN", "ACNE_BLEMISH" -> List.of(
                    new StageTemplate("CALM", "Clear & prep", "Reduce surface oil and prep pores without stripping.",
                            "Oiliness {oiliness}/100 and {primary} are the priority — start with decongestion before stronger correction."),
                    new StageTemplate("CORRECT", "Treat & refine", "Target congestion and uneven tone from week 5 onward.",
                            "Once sebum is calmer, shift to {secondary}-focused actives; texture at {texture}/100 guides intensity."),
                    new StageTemplate("MAINTAIN", "Balance & protect", "Keep T-zone clear and barrier stable long term.",
                            "By month 3, maintain results with lighter treatments; redness {redness}/100 should stay below prior baseline."));
            case "DRY_DEHYDRATED" -> List.of(
                    new StageTemplate("HYDRATE", "Replenish barrier", "Rebuild moisture and calm tight, flaky areas.",
                            "Hydration index {hydration}/100 is low — month 1 focuses on humectants and barrier repair, not harsh exfoliation."),
                    new StageTemplate("NOURISH", "Deep nourishment", "Layer lipids and glow treatments as skin tolerates.",
                            "With barrier improving, address {secondary}; avoid over-peeling while dryness signals remain."),
                    new StageTemplate("GLOW", "Lock in radiance", "Maintain suppleness and even light reflection.",
                            "Month 3 emphasizes lasting glow and SPF discipline; health score {health}/100 guides maintenance frequency."));
            case "REDNESS_SENSITIVITY" -> List.of(
                    new StageTemplate("SOOTHE", "Calm & stabilize", "Lower visible redness and strengthen barrier.",
                            "Redness {redness}/100 suggests reactive skin — gentle, fragrance-light facials first."),
                    new StageTemplate("REPAIR", "Barrier recovery", "Introduce mild brightening only if skin stays calm.",
                            "After soothing, lightly address {secondary} without triggering flare-ups."),
                    new StageTemplate("PROTECT", "Maintain calm", "Sustain even tone with minimal irritation risk.",
                            "Long-term plan favors maintenance facials and strict SPF; reassess if redness spikes."));
            case "UNEVEN_TEXTURE", "DULLNESS" -> List.of(
                    new StageTemplate("POLISH", "Surface renewal", "Gentle exfoliation and brightness prep.",
                            "Texture {texture}/100 and dullness call for controlled renewal — not maximum peel strength in month 1."),
                    new StageTemplate("BRIGHTEN", "Even tone", "Build on smoother surface with targeted brightening.",
                            "Month 2 targets {secondary} while monitoring redness {redness}/100."),
                    new StageTemplate("RADIANCE", "Sustain glow", "Maintain clarity with seasonal maintenance.",
                            "Month 3 consolidates results; home actives taper if skin feels sensitized."));
            default -> List.of(
                    new StageTemplate("BASE", "Foundation care", "Establish baseline cleanse, treat, moisturize, SPF.",
                            "Scan shows relatively {primary}; month 1 sets habits using your actual metric profile (health {health}/100)."),
                    new StageTemplate("ENHANCE", "Targeted boost", "Add one focus area from secondary signals.",
                            "Introduce {secondary}-aligned treatment as skin tolerates; texture {texture}/100 informs exfoliation frequency."),
                    new StageTemplate("SUSTAIN", "Long-term maintenance", "Keep results with lighter facials and retail refill.",
                            "Ongoing plan alternates hydration and polish based on season — not the same facial each visit."));
        };
    }

    private String fillRationale(
            String template,
            FaceScanMetricsDto metrics,
            List<FaceScanConcernDto> concerns,
            String targetConcern,
            int month) {
        String primary = concerns.isEmpty() ? "balanced skin" : concerns.get(0).getLabel().toLowerCase(Locale.ROOT);
        String secondary = concerns.size() > 1 ? concerns.get(1).getLabel().toLowerCase(Locale.ROOT) : primary;
        return template
                .replace("{oiliness}", String.valueOf(metrics.getOilinessIndex()))
                .replace("{hydration}", String.valueOf(metrics.getHydrationIndex()))
                .replace("{texture}", String.valueOf(metrics.getTextureIndex()))
                .replace("{redness}", String.valueOf(metrics.getRednessIndex()))
                .replace("{health}", String.valueOf(metrics.getSkinHealthScore()))
                .replace("{primary}", primary)
                .replace("{secondary}", secondary)
                .replace("{concern}", labelFor(targetConcern).toLowerCase(Locale.ROOT))
                .replace("{month}", String.valueOf(month));
    }

    private List<ScoredCatalogEntry> rankCatalog(
            List<CatalogServiceRef> catalog,
            List<String> codes,
            FaceScanMetricsDto metrics) {
        if (catalog == null || catalog.isEmpty()) {
            return List.of();
        }
        List<ScoredCatalogEntry> entries = new ArrayList<>();
        for (CatalogServiceRef ref : catalog) {
            String lower = ref.name().toLowerCase(Locale.ROOT);
            for (String code : codes.isEmpty() ? List.of("BALANCED") : codes) {
                int score = scoreServiceName(lower, code, metrics);
                if (score > 0) {
                    entries.add(new ScoredCatalogEntry(ref, score, code, keywordKind(lower, code)));
                }
            }
        }
        entries.sort(Comparator.comparingInt(ScoredCatalogEntry::score).reversed());
        if (entries.isEmpty()) {
            for (CatalogServiceRef ref : catalog) {
                entries.add(new ScoredCatalogEntry(ref, 10, "BALANCED", "general"));
            }
        }
        return entries;
    }

    private ScoredCatalogEntry pickServiceForStage(
            List<ScoredCatalogEntry> ranked,
            String targetConcern,
            String stageKey,
            int varietySeed,
            int month,
            Set<UUID> usedIds) {
        if (ranked.isEmpty()) {
            return null;
        }
        List<String> stageKeywords = stageKeywords(stageKey, targetConcern);
        List<ScoredCatalogEntry> pool = ranked.stream()
                .filter(e -> e.concernCode().equals(targetConcern)
                        || stageKeywords.stream().anyMatch(k -> e.service().name().toLowerCase(Locale.ROOT).contains(k)))
                .filter(e -> e.service().branchServiceId() == null || !usedIds.contains(e.service().branchServiceId()))
                .toList();
        if (pool.isEmpty()) {
            pool = ranked.stream()
                    .filter(e -> e.service().branchServiceId() == null || !usedIds.contains(e.service().branchServiceId()))
                    .toList();
        }
        if (pool.isEmpty()) {
            pool = ranked;
        }
        int idx = Math.floorMod(varietySeed + month * 17 + stageKey.hashCode(), pool.size());
        return pool.get(idx);
    }

    private FaceScanServiceSuggestionDto toVisitSuggestion(
            ScoredCatalogEntry pick,
            String targetConcern,
            FaceScanMetricsDto metrics,
            List<FaceScanConcernDto> concerns,
            int month,
            StageTemplate template) {
        if (pick == null) {
            return defaultVisitForStage(targetConcern, month, metrics, template);
        }
        String reason = buildServiceReason(pick, targetConcern, metrics, concerns, month, template);
        return FaceScanServiceSuggestionDto.builder()
                .branchServiceId(pick.service().branchServiceId())
                .name(pick.service().name())
                .reason(reason)
                .build();
    }

    private String buildServiceReason(
            ScoredCatalogEntry pick,
            String targetConcern,
            FaceScanMetricsDto metrics,
            List<FaceScanConcernDto> concerns,
            int month,
            StageTemplate template) {
        String concernLabel = labelFor(targetConcern);
        Optional<FaceScanConcernDto> concernDto = concerns.stream().filter(c -> c.getCode().equals(targetConcern)).findFirst();
        String severity = concernDto.map(FaceScanConcernDto::getSeverity).orElse("LOW");
        String metricLine = metricLineForConcern(targetConcern, metrics);
        return String.format(
                Locale.ROOT,
                "Month %d (%s): chosen for %s (%s priority). %s %s — this visit supports \"%s\" because %s.",
                month,
                template.titleSuffix(),
                concernLabel,
                severity,
                metricLine,
                pick.matchKind() != null ? "Menu match: " + pick.matchKind() + "." : "",
                pick.service().name(),
                benefitForStage(template.stageKey(), targetConcern));
    }

    private static String metricLineForConcern(String code, FaceScanMetricsDto m) {
        return switch (code) {
            case "OILY_SKIN", "ACNE_BLEMISH" -> "Oiliness signal " + m.getOilinessIndex() + "/100";
            case "DRY_DEHYDRATED" -> "Hydration index " + m.getHydrationIndex() + "/100";
            case "REDNESS_SENSITIVITY" -> "Redness index " + m.getRednessIndex() + "/100";
            case "UNEVEN_TEXTURE", "DULLNESS" -> "Texture index " + m.getTextureIndex() + "/100";
            default -> "Skin health score " + m.getSkinHealthScore() + "/100";
        };
    }

    private static String benefitForStage(String stageKey, String concern) {
        return switch (stageKey) {
            case "CALM", "CLEAR" -> "it clears excess sebum before stronger treatments";
            case "CORRECT" -> "it addresses " + labelFor(concern).toLowerCase(Locale.ROOT) + " with appropriate actives";
            case "HYDRATE", "SOOTHE" -> "it restores comfort without stripping the barrier";
            case "NOURISH", "REPAIR" -> "it feeds the skin as tolerance improves";
            case "POLISH", "BRIGHTEN" -> "it improves surface smoothness and light reflection";
            case "GLOW", "RADIANCE", "MAINTAIN", "SUSTAIN", "PROTECT" -> "it preserves results between stronger phases";
            default -> "it matches this stage of your personalized program";
        };
    }

    private FaceScanServiceSuggestionDto defaultVisitForStage(
            String targetConcern,
            int month,
            FaceScanMetricsDto metrics,
            StageTemplate template) {
        String name = switch (targetConcern) {
            case "OILY_SKIN", "ACNE_BLEMISH" -> month == 1 ? "Deep cleansing facial" : month == 2 ? "Anti-acne / decongest facial" : "Balancing facial";
            case "DRY_DEHYDRATED" -> month == 1 ? "Hydrating facial" : month == 2 ? "Nourishing moisture facial" : "Glow maintenance facial";
            case "REDNESS_SENSITIVITY" -> month == 1 ? "Calming sensitive-skin facial" : month == 2 ? "Barrier repair facial" : "Gentle maintenance facial";
            case "UNEVEN_TEXTURE", "DULLNESS" -> month == 1 ? "Gentle polish facial" : month == 2 ? "Brightening facial" : "Radiance facial";
            default -> month == 1 ? "Signature facial" : month == 2 ? "Custom boost facial" : "Seasonal maintenance facial";
        };
        return svc(null, name, buildServiceReason(
                new ScoredCatalogEntry(new CatalogServiceRef(null, name), 50, targetConcern, "recommended template"),
                targetConcern,
                metrics,
                List.of(),
                month,
                template));
    }

    private List<FaceScanRoutineStepDto> homeRoutineForMonth(
            int month,
            List<String> codes,
            FaceScanMetricsDto metrics,
            String focusConcern) {
        boolean oily = codes.contains("OILY_SKIN") || codes.contains("ACNE_BLEMISH");
        boolean dry = codes.contains("DRY_DEHYDRATED");
        boolean red = codes.contains("REDNESS_SENSITIVITY");
        boolean texture = codes.contains("UNEVEN_TEXTURE") || codes.contains("DULLNESS");

        return switch (month) {
            case 1 -> List.of(
                    homeStep(1, "CLEANSE", "Reset cleanse",
                            oily ? "AM/PM gel cleanser on T-zone; skip harsh scrubs this month."
                                    : dry ? "Cream cleanser only; pat dry — do not rub."
                                    : "Gentle pH-balanced cleanse twice daily.",
                            "Removes buildup so in-salon work penetrates evenly."),
                    homeStep(2, "TREAT", "Stage-1 serum",
                            red ? "Niacinamide or centella on calm skin only."
                                    : oily ? "Salicylic 0.5–1% on oily zones 3× week."
                                    : "Hyaluronic on damp skin if hydration " + metrics.getHydrationIndex() + "/100 is low.",
                            "Targets " + labelFor(focusConcern).toLowerCase(Locale.ROOT) + " without over-treating."),
                    homeStep(3, "MOISTURIZE", "Barrier moisturizer",
                            dry ? "Ceramide-rich cream AM/PM." : oily ? "Oil-free gel moisturizer." : "Lightweight lotion.",
                            "Stabilizes skin between salon visits."),
                    homeStep(4, "PROTECT", "SPF daily",
                            "SPF 30+ every morning — non-negotiable after any brightening step.",
                            "Prevents new tone unevenness while month-1 treatments take effect."));
            case 2 -> List.of(
                    homeStep(1, "CLEANSE", "Maintain cleanse", oily ? "Same gel routine; add lukewarm double cleanse if wearing SPF." : "Keep gentle cleanser; no new acids yet if redness flares.", "Consistent cleansing keeps pores from refilling after month-1 facial."),
                    homeStep(2, "TREAT", "Stage-2 active",
                            texture ? "Introduce mild AHA 1–2× week if texture index is " + metrics.getTextureIndex() + "/100." : "Antioxidant serum in AM.",
                            "Aligns with month-2 in-salon correction phase."),
                    homeStep(3, "CORRECT", "Spot care",
                            codes.contains("ACNE_BLEMISH") ? "Benzoyl peroxide spot dots only on blemishes." : "Weekly enzyme mask if skin feels rough.",
                            "Localized treatment avoids irritating entire face."),
                    homeStep(4, "PROTECT", "SPF + barrier",
                            "Same SPF; add night moisturizer if any tightness after actives.",
                            "Protects progressing skin while actives increase."));
            default -> List.of(
                    homeStep(1, "CLEANSE", "Maintenance cleanse", "Keep proven cleanser from months 1–2.", "Stable routine reduces rebound breakouts or dryness."),
                    homeStep(2, "TREAT", "Maintain serum", red ? "Soothing serum only; pause strong acids if redness returns." : "Alternate vit C (AM) and niacinamide (PM) if tolerated.", "Sustains results without re-aggravating " + labelFor(focusConcern).toLowerCase(Locale.ROOT) + "."),
                    homeStep(3, "MOISTURIZE", "Seasonal adjust", dry ? "Richer cream in dry weather." : oily ? "Lighter gel in humid months." : "Same lightweight moisturizer.", "Adapts to climate so maintenance facials last longer."),
                    homeStep(4, "PROTECT", "SPF forever", "Daily SPF; reapply if outdoors.", "Locks in month 2–3 brightening and prevents new damage."));
        };
    }

    private static FaceScanRoutineStepDto homeStep(int n, String phase, String title, String description, String why) {
        return FaceScanRoutineStepDto.builder()
                .step(n)
                .phase(phase)
                .title(title)
                .description(description + " Why it helps: " + why)
                .build();
    }

    private static int scoreServiceName(String lower, String code, FaceScanMetricsDto metrics) {
        int score = 0;
        for (String kw : keywordsForConcern(code)) {
            if (lower.contains(kw)) {
                score += 15;
            }
        }
        score += switch (code) {
            case "OILY_SKIN", "ACNE_BLEMISH" -> metrics.getOilinessIndex() / 10;
            case "DRY_DEHYDRATED" -> (100 - metrics.getHydrationIndex()) / 10;
            case "REDNESS_SENSITIVITY" -> metrics.getRednessIndex() / 10;
            case "UNEVEN_TEXTURE", "DULLNESS" -> metrics.getTextureIndex() / 10;
            default -> metrics.getSkinHealthScore() / 15;
        };
        return score;
    }

    private static String keywordKind(String lower, String code) {
        for (String kw : keywordsForConcern(code)) {
            if (lower.contains(kw)) {
                return kw;
            }
        }
        return "facial menu";
    }

    private static List<String> stageKeywords(String stageKey, String concern) {
        List<String> keys = new ArrayList<>(keywordsForConcern(concern));
        keys.addAll(switch (stageKey) {
            case "CALM", "CLEAR" -> List.of("clean", "deep", "decongest", "acne");
            case "CORRECT" -> List.of("peel", "acne", "bright", "detan");
            case "HYDRATE", "NOURISH" -> List.of("hydra", "moist", "lotus", "fruit", "glow");
            case "SOOTHE", "REPAIR" -> List.of("calm", "sensitive", "sooth");
            case "POLISH", "BRIGHTEN", "GLOW", "RADIANCE" -> List.of("bright", "glow", "light", "fruit", "vlcc", "sara");
            default -> List.of("facial", "skin");
        });
        return keys;
    }

    private static List<String> keywordsForConcern(String code) {
        return switch (code) {
            case "OILY_SKIN", "ACNE_BLEMISH" -> List.of("clean", "cleanup", "acne", "oily", "decongest", "purif");
            case "DRY_DEHYDRATED" -> List.of("hydra", "moist", "nourish", "lotus", "dry");
            case "REDNESS_SENSITIVITY" -> List.of("calm", "sooth", "sensitive", "redness");
            case "UNEVEN_TEXTURE", "DULLNESS" -> List.of("bright", "glow", "light", "detan", "polish", "fruit");
            case "COMBINATION_SKIN" -> List.of("balance", "combo", "facial");
            default -> List.of("facial", "signature", "fruit", "lotus", "skin");
        };
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
            case "BALANCED" -> "Balanced skin";
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
