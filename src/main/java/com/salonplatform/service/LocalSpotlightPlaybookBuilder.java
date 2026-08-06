package com.salonplatform.service;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.LocalCompetitor;
import com.salonplatform.domain.enums.BranchBusinessType;
import com.salonplatform.dto.analytics.LocalSpotlightResponse;
import com.salonplatform.google.LocalSpotlightKeywords;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds a deduplicated Local Spotlight action plan grouped by keyword gaps and action type.
 */
final class LocalSpotlightPlaybookBuilder {

    private static final int TARGET_RANK = 2;

    private static final Map<String, Integer> SECTION_ORDER = Map.of(
            "GOAL", 0,
            "KEYWORDS", 1,
            "PROFILE", 2,
            "REVIEWS", 3,
            "CONTENT", 4,
            "REPUTATION", 5);

    private LocalSpotlightPlaybookBuilder() {}

    static List<LocalSpotlightResponse.PlaybookItem> build(
            Branch branch,
            LocalSpotlightResponse.BranchRow row,
            List<LocalCompetitor> rivals,
            List<LocalSpotlightResponse.SearchRankRow> searchRanks) {

        if (!row.isGoogleSynced()) {
            return List.of(item(
                    "sync-" + row.getBranchId(),
                    "HIGH",
                    "GOAL",
                    "Sync Google data — " + row.getBranchName(),
                    "Refresh from Google to pull live listing metrics, keyword ranks, and nearby rivals.",
                    "Google has not been queried yet — the action plan needs a fresh sync to compare you vs rivals.",
                    List.of(),
                    "SYNC",
                    "action:syncGoogle",
                    "Refresh from Google",
                    row));
        }

        if (!row.isListingLinked()) {
            return List.of(item(
                    "link-" + row.getBranchId(),
                    "HIGH",
                    "GOAL",
                    "Confirm Google Business Profile — " + row.getBranchName(),
                    "Verify branch name, address, and pin on Google Maps, then refresh Local Spotlight.",
                    "Without a linked listing we cannot track ranks or benchmark against rivals.",
                    List.of(),
                    "LISTING",
                    "action:syncGoogle",
                    "Refresh from Google",
                    row));
        }

        RivalBenchmark bench = analyzeRivals(rivals);
        List<LocalSpotlightResponse.SearchRankRow> branchRanks = searchRanks.stream()
                .filter(r -> r.getBranchId().equals(row.getBranchId()))
                .toList();

        List<LocalSpotlightResponse.PlaybookItem> items = new ArrayList<>();
        items.add(buildGoalSummary(row, branchRanks, bench));
        items.addAll(buildKeywordSection(row, branchRanks, bench, branch));
        items.addAll(buildProfileActions(row, bench));
        items.addAll(buildReviewActions(row, bench, branchRanks));
        items.addAll(buildContentActions(row, bench, branchRanks));
        items.addAll(buildReputationActions(row));

        items.sort(Comparator
                .comparingInt((LocalSpotlightResponse.PlaybookItem i) ->
                        SECTION_ORDER.getOrDefault(i.getSection(), 99))
                .thenComparingInt(i -> metricKeyOrder(i.getMetricKey()))
                .thenComparingInt(i -> severityOrder(i.getSeverity()))
                .thenComparing(LocalSpotlightResponse.PlaybookItem::getTitle));

        return items;
    }

    private static List<LocalSpotlightResponse.PlaybookItem> buildKeywordSection(
            LocalSpotlightResponse.BranchRow row,
            List<LocalSpotlightResponse.SearchRankRow> ranks,
            RivalBenchmark bench,
            Branch branch) {

        List<LocalSpotlightResponse.PlaybookItem> items = new ArrayList<>();
        if (ranks.isEmpty()) {
            return items;
        }

        List<LocalSpotlightResponse.SearchRankRow> behindTarget = ranks.stream()
                .filter(r -> r.getYourRank() == null || r.getYourRank() > TARGET_RANK)
                .toList();

        if (behindTarget.isEmpty()) {
            items.add(item(
                    "kw-ok-" + row.getBranchId(),
                    "LOW",
                    "KEYWORDS",
                    "All tracked keywords in top " + TARGET_RANK,
                    "Maintain review velocity and weekly Google posts to defend map-pack positions.",
                    "Every synced keyword is currently at rank #" + TARGET_RANK + " or better.",
                    ranks.stream().map(LocalSpotlightResponse.SearchRankRow::getKeyword).toList(),
                    "KEYWORDS",
                    "tab:search",
                    "View search visibility",
                    row));
            return items;
        }

        items.add(buildKeywordScorecard(row, ranks, bench));

        List<LocalSpotlightResponse.SearchRankRow> notInTop20 = behindTarget.stream()
                .filter(r -> r.getYourRank() == null || r.isYourRankBeyondTop20())
                .toList();
        List<LocalSpotlightResponse.SearchRankRow> inPackBehind = behindTarget.stream()
                .filter(r -> r.getYourRank() != null && r.getYourRank() > TARGET_RANK)
                .toList();

        if (!notInTop20.isEmpty()) {
            items.add(buildGroupedKeywordAction(
                    row,
                    notInTop20,
                    bench,
                    branch,
                    "HIGH",
                    "Break into top 20 — " + notInTop20.size() + " keyword"
                            + (notInTop20.size() == 1 ? "" : "s"),
                    "Same root causes affect every query below: review volume (you "
                            + (row.getGoogleReviewCount() != null ? row.getGoogleReviewCount() : 0)
                            + " vs leaders at " + bench.avgTopRivalReviews() + "+), profile completeness ("
                            + row.getGbpCompletenessPercent() + "%), and category depth on Google. "
                            + "Complete the Profile and Reviews sections below — they lift all keywords together. "
                            + "Then split salon vs spa services on GBP so both keyword families rank.",
                    "NOT_IN_TOP_20"));
        }

        if (!inPackBehind.isEmpty()) {
            items.add(buildGroupedKeywordAction(
                    row,
                    inPackBehind,
                    bench,
                    branch,
                    "HIGH",
                    "Reach top " + TARGET_RANK + " — " + inPackBehind.size() + " keyword"
                            + (inPackBehind.size() == 1 ? "" : "s") + " already in map pack",
                    "You already appear in results — focus on review velocity (+15–20/month), "
                            + "5 fresh photos per service theme, and owner responses that mention the exact keyword + locality.",
                    "IN_MAP_PACK"));
        }

        BranchBusinessType type = LocalSpotlightKeywords.effectiveType(branch);
        List<String> spaBehind = behindTarget.stream()
                .filter(r -> r.getKeyword().toLowerCase().contains("spa"))
                .map(LocalSpotlightResponse.SearchRankRow::getKeyword)
                .toList();
        List<String> salonBehind = behindTarget.stream()
                .filter(r -> r.getKeyword().toLowerCase().contains("salon"))
                .map(LocalSpotlightResponse.SearchRankRow::getKeyword)
                .toList();

        if ((type == BranchBusinessType.SALON_AND_SPA || type == BranchBusinessType.SPA) && !spaBehind.isEmpty()) {
            String spaLeaders = frequentLeaders(behindTarget.stream()
                    .filter(r -> r.getKeyword().toLowerCase().contains("spa"))
                    .toList(), 3);
            items.add(item(
                    "kw-spa-focus-" + row.getBranchId(),
                    "MEDIUM",
                    "KEYWORDS",
                    "Spa positioning (" + spaBehind.size() + " spa queries behind)",
                    "Add Day Spa category, spa-only photo album, and services: massage, body spa, couple spa. "
                            + "Ask spa guests for reviews mentioning \"spa\" and your locality.",
                    "Spa leaders across your queries: " + spaLeaders + ".",
                    spaBehind,
                    "SPA_FOCUS",
                    "tab:search",
                    "View spa keywords",
                    row));
        }

        if ((type == BranchBusinessType.SALON_AND_SPA || type == BranchBusinessType.SALON) && !salonBehind.isEmpty()) {
            String salonLeaders = frequentLeaders(behindTarget.stream()
                    .filter(r -> r.getKeyword().toLowerCase().contains("salon"))
                    .toList(), 3);
            items.add(item(
                    "kw-salon-focus-" + row.getBranchId(),
                    "MEDIUM",
                    "KEYWORDS",
                    "Salon positioning (" + salonBehind.size() + " salon queries behind)",
                    "Highlight hair, unisex, and grooming as separate GBP services. "
                            + "Match breadth of family-salon rivals (STUDIO11-style menus).",
                    "Salon leaders across your queries: " + salonLeaders + ".",
                    salonBehind,
                    "SALON_FOCUS",
                    "tab:search",
                    "View salon keywords",
                    row));
        }

        return items;
    }

    private static LocalSpotlightResponse.PlaybookItem buildKeywordScorecard(
            LocalSpotlightResponse.BranchRow row,
            List<LocalSpotlightResponse.SearchRankRow> ranks,
            RivalBenchmark bench) {

        StringBuilder reasoning = new StringBuilder();
        for (LocalSpotlightResponse.SearchRankRow rankRow : ranks) {
            if (reasoning.length() > 0) {
                reasoning.append(" ");
            }
            String rankLabel = rankRow.getYourRank() != null
                    ? "#" + rankRow.getYourRank()
                    : "not in top 20";
            String leaders = formatLeaders(rankRow, 2);
            reasoning.append("• \"").append(rankRow.getKeyword()).append("\" — ")
                    .append(rankLabel);
            if (!leaders.isBlank()) {
                reasoning.append(" · leads: ").append(leaders);
            }
            reasoning.append(". ");
        }

        int behind = (int) ranks.stream()
                .filter(r -> r.getYourRank() == null || r.getYourRank() > TARGET_RANK)
                .count();

        return item(
                "kw-scorecard-" + row.getBranchId(),
                "MEDIUM",
                "KEYWORDS",
                "Keyword scorecard — " + behind + " of " + ranks.size() + " need top " + TARGET_RANK,
                "Use this snapshot to track progress after each Google refresh. "
                        + "Actions below are grouped — one fix often lifts multiple keywords.",
                reasoning.toString().trim(),
                ranks.stream().map(LocalSpotlightResponse.SearchRankRow::getKeyword).toList(),
                "SCORECARD",
                "tab:search",
                "View search visibility",
                row);
    }

    private static LocalSpotlightResponse.PlaybookItem buildGroupedKeywordAction(
            LocalSpotlightResponse.BranchRow row,
            List<LocalSpotlightResponse.SearchRankRow> group,
            RivalBenchmark bench,
            Branch branch,
            String severity,
            String title,
            String message,
            String metricKey) {

        List<String> keywords = group.stream()
                .map(LocalSpotlightResponse.SearchRankRow::getKeyword)
                .toList();

        StringBuilder reasoning = new StringBuilder();
        for (LocalSpotlightResponse.SearchRankRow rankRow : group) {
            if (reasoning.length() > 0) {
                reasoning.append(" ");
            }
            String rankLabel = rankRow.getYourRank() != null
                    ? "#" + rankRow.getYourRank()
                    : "not in top 20";
            reasoning.append("• \"").append(rankRow.getKeyword()).append("\" — ")
                    .append(rankLabel);
            String leaders = formatLeaders(rankRow, 3);
            if (!leaders.isBlank()) {
                reasoning.append(" · ").append(leaders);
            }
            reasoning.append(". ");
        }

        String frequent = frequentLeaders(group, 2);
        if (!frequent.isBlank()) {
            reasoning.append("Repeat winners to study: ").append(frequent).append(". ");
        }
        reasoning.append("Rivals avg ~").append(bench.avgTopRivalReviews()).append(" reviews.");

        return item(
                "kw-group-" + metricKey.toLowerCase() + "-" + row.getBranchId(),
                severity,
                "KEYWORDS",
                title,
                message,
                reasoning.toString().trim(),
                keywords,
                metricKey,
                    "tab:search",
                    "View keyword ranks",
                row);
    }

    private static LocalSpotlightResponse.PlaybookItem buildGoalSummary(
            LocalSpotlightResponse.BranchRow row,
            List<LocalSpotlightResponse.SearchRankRow> ranks,
            RivalBenchmark bench) {

        int inTop2 = (int) ranks.stream()
                .filter(r -> r.getYourRank() != null && r.getYourRank() <= TARGET_RANK)
                .count();
        int inTop20 = (int) ranks.stream()
                .filter(r -> r.getYourRank() != null)
                .count();
        int beyondTop20 = (int) ranks.stream()
                .filter(r -> r.getYourRank() == null && r.isYourRankBeyondTop20())
                .count();
        Integer bestRank = ranks.stream()
                .map(LocalSpotlightResponse.SearchRankRow::getYourRank)
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(null);

        String reasoning = String.format(
                "LVS %d/100 · %d/%d keywords in top %d · %d in top 20 · %d not in top 20 · "
                        + "you: %d reviews ★%.1f · top rivals avg %d reviews ★%.1f.",
                row.getLocalVisibilityScore(),
                inTop2,
                ranks.size(),
                TARGET_RANK,
                inTop20,
                beyondTop20,
                row.getGoogleReviewCount() != null ? row.getGoogleReviewCount() : 0,
                row.getGoogleRating() != null ? row.getGoogleRating() : 0,
                bench.avgTopRivalReviews(),
                bench.avgTopRivalRating());

        String message = bestRank != null
                ? "Best rank #" + bestRank + ". Work through sections below in order: Keyword gaps → Profile → Reviews → Content."
                : "Most keywords are outside the top 20. Foundation fixes (profile + reviews) come before keyword tuning.";

        return item(
                "goal-" + row.getBranchId(),
                inTop2 >= ranks.size() ? "LOW" : "HIGH",
                "GOAL",
                "Reach top " + TARGET_RANK + " on all " + ranks.size() + " local keywords",
                message,
                reasoning,
                ranks.stream().map(LocalSpotlightResponse.SearchRankRow::getKeyword).toList(),
                "GOAL",
                "tab:search",
                "View search visibility",
                row);
    }

    private static List<LocalSpotlightResponse.PlaybookItem> buildProfileActions(
            LocalSpotlightResponse.BranchRow row,
            RivalBenchmark bench) {

        List<LocalSpotlightResponse.PlaybookItem> items = new ArrayList<>();
        int photos = row.getGbpPhotoCount() != null ? row.getGbpPhotoCount() : 0;
        int completeness = row.getGbpCompletenessPercent();
        List<String> missing = new ArrayList<>();
        if (!row.isGbpHasPhone()) missing.add("phone");
        if (!row.isGbpHasWebsite()) missing.add("website");
        if (!row.isGbpHasBookButton()) missing.add("book button");
        if (!row.isGbpHasHours()) missing.add("hours");
        if ((row.getGbpVideoCount() != null ? row.getGbpVideoCount() : 0) < 1) missing.add("video");
        if ((row.getGbpServicesListedCount() != null ? row.getGbpServicesListedCount() : 0) < 8) missing.add("8+ services");

        if (completeness < 85 || !missing.isEmpty()) {
            items.add(item(
                    "complete-" + row.getBranchId(),
                    "HIGH",
                    "PROFILE",
                    "Complete Google Business Profile (" + completeness + "% → 85%+)",
                    "Fix: " + (missing.isEmpty() ? "raise completeness" : String.join(", ", missing))
                            + ", 15+ photos (salon + spa zones), separate service menus for dual-format branches.",
                    "Profile at " + completeness + "% — incomplete listings lose map-pack tie-breakers vs rivals "
                            + "with full phone, web, book, hours, and service depth.",
                    List.of(),
                    "COMPLETE",
                    "tab:branches",
                    "Open branch checklist",
                    row));
        } else if (photos < 15) {
            items.add(item(
                    "photos-" + row.getBranchId(),
                    "HIGH",
                    "PROFILE",
                    "Upload 15+ high-quality Google photos",
                    "Exterior, reception, stations, spa rooms, team, before/after (with consent). "
                            + "Captions should mention salon/spa + locality.",
                    "You have " + photos + " photos"
                            + (bench.avgRivalPhotos() > 0 ? " · rivals avg " + bench.avgRivalPhotos() : "")
                            + ". Photo-rich listings win clicks from map pack.",
                    List.of(),
                    "PHOTOS",
                    "external:googleMaps",
                    "Open Google listing",
                    row));
        }

        return items;
    }

    private static List<LocalSpotlightResponse.PlaybookItem> buildReviewActions(
            LocalSpotlightResponse.BranchRow row,
            RivalBenchmark bench,
            List<LocalSpotlightResponse.SearchRankRow> ranks) {

        List<LocalSpotlightResponse.PlaybookItem> items = new ArrayList<>();
        int reviews = row.getGoogleReviewCount() != null ? row.getGoogleReviewCount() : 0;
        int targetReviews = Math.max(200, (int) (bench.medianRivalReviews() * 0.4));
        int gap = Math.max(0, targetReviews - reviews);
        long keywordsBehind = ranks.stream()
                .filter(r -> r.getYourRank() == null || r.getYourRank() > TARGET_RANK)
                .count();

        if (reviews < targetReviews) {
            items.add(item(
                    "reviews-" + row.getBranchId(),
                    "HIGH",
                    "REVIEWS",
                    "Grow Google reviews — " + reviews + " → " + targetReviews + "+ target",
                    "Customer Voice after 4–5★ visits. Ask guests to mention service + locality in text "
                            + "(e.g. \"best spa near Varthur\"). Aim +" + Math.min(gap, 120) + " in 90 days. "
                            + "Reply to every review within 48h with keyword-rich thank-yous.",
                    "Largest shared gap across " + keywordsBehind + " keywords: you have " + reviews
                            + " reviews vs top rivals at " + bench.topRivalReviews() + " (" + bench.topRivalName()
                            + "). Google prominence heavily weights review count.",
                    List.of(),
                    "REVIEWS",
                    "route:/admin/guest-voice",
                    "Open Customer Voice",
                    row));
        } else if (row.getGoogleRating() != null && row.getGoogleRating() < bench.avgTopRivalRating() - 0.05) {
            items.add(item(
                    "rating-" + row.getBranchId(),
                    "MEDIUM",
                    "REVIEWS",
                    "Protect star rating (★" + String.format("%.1f", row.getGoogleRating()) + " vs ★"
                            + String.format("%.1f", bench.avgTopRivalRating()) + " rivals)",
                    "Resolve issues before they become public 1–3★ reviews. Follow up sub-4★ internal ratings within 24h.",
                    "Rating trails map-pack leaders who hold multiple top-3 keyword slots.",
                    List.of(),
                    "RATING",
                    "route:/admin/guest-voice",
                    "Review recovery",
                    row));
        }

        return items;
    }

    private static List<LocalSpotlightResponse.PlaybookItem> buildContentActions(
            LocalSpotlightResponse.BranchRow row,
            RivalBenchmark bench,
            List<LocalSpotlightResponse.SearchRankRow> ranks) {

        List<String> sampleKeywords = ranks.stream()
                .limit(3)
                .map(LocalSpotlightResponse.SearchRankRow::getKeyword)
                .toList();

        return List.of(
                item(
                        "content-" + row.getBranchId(),
                        "MEDIUM",
                        "CONTENT",
                        "Ongoing local SEO content",
                        "• 2 Google posts/week — rotate salon offers, spa packages, team spotlights\n"
                                + "• Seed Q&A: \"Do you offer spa?\", \"Best salon near here?\"\n"
                                + "• Keep NAP identical on Google, website, Instagram, directories",
                        "One content rhythm supports all keywords. Rivals in multiple top-3 slots post weekly "
                                + "and maintain consistent NAP.",
                        sampleKeywords,
                    "CONTENT",
                    "external:googleMaps",
                    "Open Google listing",
                        row));
    }

    private static List<LocalSpotlightResponse.PlaybookItem> buildReputationActions(
            LocalSpotlightResponse.BranchRow row) {

        int low = row.getGoogleLowRatingReviewCount() != null ? row.getGoogleLowRatingReviewCount() : 0;
        int sample = row.getGoogleReviewsSampleSize() != null ? row.getGoogleReviewsSampleSize() : 0;

        if (low <= 0) {
            return List.of();
        }

        return List.of(item(
                "recovery-" + row.getBranchId(),
                "HIGH",
                "REPUTATION",
                "Recover from " + low + " sub-4★ reviews (of " + sample + " sampled)",
                "Publicly respond with empathy + resolution. Fix root cause and invite an updated visit.",
                low + " recent public reviews under 4★ suppress local pack rank even when headline rating looks fine.",
                List.of(),
                "RECOVERY",
                "route:/admin/guest-voice",
                "Review recovery",
                row));
    }

    private static String formatLeaders(LocalSpotlightResponse.SearchRankRow rankRow, int max) {
        List<LocalSpotlightResponse.TopThreeRival> top = rankRow.getTopThreeRivals();
        if (top == null || top.isEmpty()) {
            return "";
        }
        return top.stream()
                .limit(max)
                .map(r -> r.getName())
                .collect(Collectors.joining(", "));
    }

    private static String frequentLeaders(List<LocalSpotlightResponse.SearchRankRow> rows, int maxNames) {
        Map<String, Long> counts = rows.stream()
                .flatMap(r -> (r.getTopThreeRivals() != null ? r.getTopThreeRivals() : List.<LocalSpotlightResponse.TopThreeRival>of()).stream())
                .map(LocalSpotlightResponse.TopThreeRival::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(n -> n, Collectors.counting()));

        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(maxNames)
                .map(e -> e.getKey() + (e.getValue() > 1 ? " (" + e.getValue() + " keywords)" : ""))
                .collect(Collectors.joining(", "));
    }

    private static RivalBenchmark analyzeRivals(List<LocalCompetitor> rivals) {
        if (rivals.isEmpty()) {
            return new RivalBenchmark(0, 0, 0, "top rivals", 4.5, 10);
        }

        List<LocalCompetitor> sortedByReviews = rivals.stream()
                .filter(r -> r.getGoogleReviewCount() != null)
                .sorted(Comparator.comparing(LocalCompetitor::getGoogleReviewCount).reversed())
                .toList();

        List<Integer> reviewCounts = sortedByReviews.stream()
                .map(LocalCompetitor::getGoogleReviewCount)
                .toList();

        int topReviews = reviewCounts.isEmpty() ? 0 : reviewCounts.get(0);
        String topName = sortedByReviews.isEmpty() ? "top rival" : sortedByReviews.get(0).getName();

        int median = reviewCounts.isEmpty() ? 0 : reviewCounts.get(reviewCounts.size() / 2);

        double avgTop3Reviews = sortedByReviews.stream()
                .limit(3)
                .mapToInt(r -> r.getGoogleReviewCount() != null ? r.getGoogleReviewCount() : 0)
                .average()
                .orElse(0);

        double avgRating = rivals.stream()
                .map(LocalCompetitor::getGoogleRating)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(4.5);

        int avgPhotos = (int) rivals.stream()
                .map(LocalCompetitor::getGbpPhotoCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(10);

        return new RivalBenchmark(
                (int) avgTop3Reviews,
                median,
                topReviews,
                topName,
                avgRating,
                avgPhotos);
    }

    private static LocalSpotlightResponse.PlaybookItem item(
            String id,
            String severity,
            String section,
            String title,
            String message,
            String reasoning,
            List<String> keywords,
            String metricKey,
            String actionTarget,
            String actionLabel,
            LocalSpotlightResponse.BranchRow row) {
        return LocalSpotlightResponse.PlaybookItem.builder()
                .id(id)
                .severity(severity)
                .section(section)
                .subCategory(subCategoryLabel(metricKey))
                .title(title)
                .message(message)
                .reasoning(reasoning)
                .keyword(keywords.size() == 1 ? keywords.get(0) : null)
                .keywords(keywords.isEmpty() ? null : keywords)
                .metricKey(metricKey)
                .actionTarget(actionTarget)
                .actionModule(legacyRoute(actionTarget))
                .actionLabel(actionLabel)
                .branchId(row.getBranchId())
                .branchName(row.getBranchName())
                .build();
    }

    private static String subCategoryLabel(String metricKey) {
        if (metricKey == null) {
            return "General";
        }
        return switch (metricKey) {
            case "GOAL" -> "Target";
            case "SCORECARD" -> "Rank snapshot";
            case "NOT_IN_TOP_20" -> "Map pack entry";
            case "IN_MAP_PACK" -> "Rank improvement";
            case "SPA_FOCUS" -> "Spa positioning";
            case "SALON_FOCUS" -> "Salon positioning";
            case "KEYWORDS" -> "All keywords";
            case "COMPLETE" -> "Profile completeness";
            case "PHOTOS" -> "Photos & media";
            case "REVIEWS" -> "Review volume";
            case "RATING" -> "Star rating";
            case "CONTENT" -> "Posts, Q&A & NAP";
            case "RECOVERY" -> "Negative reviews";
            case "SYNC", "LISTING" -> "Data sync";
            default -> "General";
        };
    }

    /** Legacy href for clients that still read actionModule. */
    private static String legacyRoute(String actionTarget) {
        if (actionTarget == null) {
            return null;
        }
        if (actionTarget.startsWith("route:")) {
            return actionTarget.substring(6);
        }
        if (actionTarget.startsWith("tab:")) {
            return "/admin/local-spotlight";
        }
        return null;
    }

    private static int severityOrder(String severity) {
        return switch (severity) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }

    private static int metricKeyOrder(String metricKey) {
        if (metricKey == null) {
            return 50;
        }
        return switch (metricKey) {
            case "GOAL" -> 0;
            case "SCORECARD" -> 1;
            case "NOT_IN_TOP_20" -> 2;
            case "IN_MAP_PACK" -> 3;
            case "SALON_FOCUS", "SPA_FOCUS" -> 4;
            default -> 10;
        };
    }

    private record RivalBenchmark(
            int avgTopRivalReviews,
            int medianRivalReviews,
            int topRivalReviews,
            String topRivalName,
            double avgTopRivalRating,
            int avgRivalPhotos) {}
}
