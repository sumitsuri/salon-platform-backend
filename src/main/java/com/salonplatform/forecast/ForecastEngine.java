package com.salonplatform.forecast;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Learns each branch's sales pattern from its own history, checks honestly how well that works on past days, picks the model
 * that did best, and forecasts the days ahead for each branch and for the brand (the sum of its branches).
 */
public final class ForecastEngine {

    /** Fewer days of sales than this and a branch is not forecast at all. */
    public static final int MIN_FORECAST_DAYS = 28;
    /** From this much history the branch can be backtested, so models can be compared. */
    public static final int MIN_BACKTEST_DAYS = Backtest.MIN_TRAIN_DAYS + Backtest.HORIZON + Backtest.STEP_DAYS;
    private static final double COMPLEX_MODEL_MUST_BEAT_BY = 0.03;

    public enum Confidence { HIGH, MEDIUM, LOW, INSUFFICIENT }

    public record Day(LocalDate date, double expected, double low, double high, List<String> events) {}

    /** How accurate the chosen model was on days it had not seen. Errors are fractions (0.08 is 8%); -1 means it could not be tested. */
    public record Quality(String model, Confidence confidence, int backtests, int trainingDays, double dailyError, double weekError,
                          double monthError, double bias) {}

    public record Totals(double next7, double next7Low, double next7High, double next30, double next30Low, double next30High) {}

    public record Result(UUID id, String name, boolean forecastable, String note, Quality quality, Totals totals, List<Day> days) {}

    /** A festival's effect on the days ahead, in rupees and as a share of normal sales. */
    public record FestivalImpact(String id, String name, LocalDate date, double impact, double percent, boolean learned, int timesSeen) {}

    public record Output(Result brand, List<Result> branches, List<FestivalImpact> festivals, LocalDate trainedThrough, LocalDate calendarEnds,
                         List<String> diagnostics) {}

    private final FestivalCalendar calendar;
    private final List<Trainer> trainers;

    public ForecastEngine(FestivalCalendar calendar, double festivalPriorWeight) {
        this.calendar = calendar;
        this.trainers = List.of(
                new ProfileTrainer(),
                new GamTrainer("trend-and-weekday", 1, false, false, festivalPriorWeight, calendar),
                new GamTrainer("trend-weekday-festivals", 2, false, true, festivalPriorWeight, calendar),
                new GamTrainer("trend-weekday-season-festivals", 3, true, true, festivalPriorWeight, calendar));
    }

    /** Forecasts from the day after each series ends up to and including {@code through}. All series must end on the same day. */
    public Output forecast(List<Series> series, LocalDate through) {
        LocalDate trainedThrough = series.stream().map(Series::end).max(LocalDate::compareTo).orElse(through);
        List<LocalDate> origins = Backtest.origins(trainedThrough);

        List<Result> branchResults = new ArrayList<>();
        List<List<Backtest.Run>> chosenRuns = new ArrayList<>();
        List<Chosen> chosen = new ArrayList<>();
        for (Series s : series) {
            Chosen c = choose(s, origins);
            chosen.add(c);
        }
        for (Chosen c : chosen) {
            branchResults.add(c.model == null ? c.unforecastable() : c.result(through, calendar));
            if (c.model != null && c.runs != null) chosenRuns.add(c.runs);
        }

        List<Chosen> usable = chosen.stream().filter(c -> c.model != null).toList();
        Result brand = brandResult(usable, branchResults, chosenRuns, through, trainedThrough);
        return new Output(brand, branchResults, festivalImpacts(usable, trainedThrough, through), trainedThrough, calendar.coverageEnd(), diagnostics(chosen));
    }

    /** One line per branch: what the chosen model predicted for 30 days against what happened, at each backtest origin. */
    private static List<String> diagnostics(List<Chosen> chosen) {
        List<String> out = new ArrayList<>();
        for (Chosen c : chosen) {
            if (c.runs == null) continue;
            StringBuilder sb = new StringBuilder(c.series.name()).append(" [").append(c.trainer.name()).append("] 30-day actual/predicted:");
            for (Backtest.Run r : c.runs) {
                if (!r.trained()) continue;
                double a = 0, p = 0;
                for (int k = 0; k < Backtest.HORIZON; k++) {
                    a += r.actual()[k];
                    p += r.predicted()[k];
                }
                sb.append(String.format(" %s %.0f/%.0f;", r.origin(), a, p));
            }
            out.add(sb.toString());
        }
        return out;
    }

    // ---- choosing a model for one branch

    private final class Chosen {
        final Series series;
        Trainer trainer;
        Trainer.Model model;
        List<Backtest.Run> runs;
        Backtest.Score score;
        String note;

        Chosen(Series series) {
            this.series = series;
        }

        Result unforecastable() {
            return new Result(series.id(), series.name(), false, note, new Quality("none", Confidence.INSUFFICIENT, 0, positiveDays(series), -1, -1, -1, 0),
                    null, List.of());
        }

        Result result(LocalDate through, FestivalCalendar cal) {
            List<LocalDate> dates = datesAfter(series.end(), through);
            double[] expected = new double[dates.size()];
            for (int i = 0; i < expected.length; i++) expected[i] = model.expected(dates.get(i));
            return buildResult(series.id(), series.name(), trainer.name(), score, series, dates, expected, cal, null);
        }
    }

    private Chosen choose(Series s, List<LocalDate> origins) {
        Chosen c = new Chosen(s);
        int days = s.length();
        int positive = positiveDays(s);
        if (days < MIN_FORECAST_DAYS || positive < 20) {
            c.note = "Needs at least four weeks of sales before it can be forecast.";
            return c;
        }
        Trainer best = trainers.get(0);
        Map<Trainer, List<Backtest.Run>> allRuns = new LinkedHashMap<>();
        Map<Trainer, Backtest.Score> scores = new LinkedHashMap<>();
        if (days >= MIN_BACKTEST_DAYS) {
            for (Trainer t : trainers) {
                List<Backtest.Run> runs = Backtest.run(s, t, origins);
                allRuns.put(t, runs);
                scores.put(t, Backtest.score(runs));
            }
            double bestScore = scores.get(best).blended();
            for (Trainer t : trainers) {
                double sc = scores.get(t).blended();
                if (t.complexity() > best.complexity() && sc < bestScore * (1 - COMPLEX_MODEL_MUST_BEAT_BY)) {
                    best = t;
                    bestScore = sc;
                }
            }
            c.runs = allRuns.get(best);
            c.score = scores.get(best);
        } else {
            c.note = "Short history: using a simple weekday pattern until there are about four months of sales.";
        }
        c.trainer = best;
        c.model = best.fit(s, s.end());
        return c;
    }

    private static int positiveDays(Series s) {
        int n = 0;
        for (double v : s.revenue()) if (v > 0) n++;
        return n;
    }

    private static List<LocalDate> datesAfter(LocalDate end, LocalDate through) {
        List<LocalDate> out = new ArrayList<>();
        for (LocalDate d = end.plusDays(1); !d.isAfter(through); d = d.plusDays(1)) out.add(d);
        return out;
    }

    // ---- turning expected values into a result with ranges and confidence

    private Result brandResult(List<Chosen> usable, List<Result> branchResults, List<List<Backtest.Run>> chosenRuns, LocalDate through,
                               LocalDate trainedThrough) {
        UUID id = new UUID(0, 0);
        if (usable.isEmpty()) {
            return new Result(id, "All branches", false, "Not enough sales history yet to forecast.",
                    new Quality("none", Confidence.INSUFFICIENT, 0, 0, -1, -1, -1, 0), null, List.of());
        }
        List<LocalDate> dates = datesAfter(trainedThrough, through);
        double[] expected = new double[dates.size()];
        for (Chosen c : usable) {
            for (int i = 0; i < expected.length; i++) expected[i] += c.model.expected(dates.get(i));
        }
        Backtest.Score score = null;
        if (chosenRuns.size() == usable.size() && !chosenRuns.isEmpty()) score = Backtest.score(Backtest.sum(chosenRuns));
        int trainingDays = usable.stream().mapToInt(c -> c.series.length()).max().orElse(0);
        String model = usable.stream().map(c -> c.trainer.name()).distinct().count() == 1 ? usable.get(0).trainer.name() : "per-branch";
        String note = usable.size() < branchResults.size() ? "Some branches have too little history and are left out." : null;
        return buildResult(id, "All branches", model, score, null, dates, expected, calendar, note, trainingDays);
    }

    private Result buildResult(UUID id, String name, String model, Backtest.Score score, Series series, List<LocalDate> dates, double[] expected,
                               FestivalCalendar cal, String note) {
        return buildResult(id, name, model, score, series, dates, expected, cal, note, series == null ? 0 : series.length());
    }

    private Result buildResult(UUID id, String name, String model, Backtest.Score score, Series series, List<LocalDate> dates, double[] expected,
                               FestivalCalendar cal, String note, int trainingDays) {
        boolean tested = score != null && score.origins() >= 2;
        double[] dailyBand = tested && score.dailyRatio() != null ? score.dailyRatio() : new double[]{0.7, 1.3};
        double[] weekBand = tested && score.weekRatio() != null ? score.weekRatio() : new double[]{0.85, 1.15};
        double[] monthBand = tested && score.monthRatio() != null ? score.monthRatio() : new double[]{0.88, 1.12};

        List<Day> days = new ArrayList<>();
        for (int i = 0; i < dates.size(); i++) {
            double widen = 1 + Math.max(0, i - Backtest.HORIZON) / 60.0;
            double lo = Math.min(0.95, dailyBand[0]);
            double hi = Math.max(1.05, dailyBand[1]);
            days.add(new Day(dates.get(i), expected[i], expected[i] * (1 - (1 - lo) * widen), expected[i] * (1 + (hi - 1) * widen), eventsOn(dates.get(i), cal)));
        }
        double s7 = sum(expected, 7), s30 = sum(expected, 30);
        double[] w = tight(weekBand, 0.9, 1.1), m = tight(monthBand, 0.92, 1.08);
        Totals totals = new Totals(s7, s7 * w[0], s7 * w[1], s30, s30 * m[0], s30 * m[1]);
        Confidence conf = !tested ? Confidence.LOW : score.month1Error() <= 0.08 ? Confidence.HIGH : score.month1Error() <= 0.15 ? Confidence.MEDIUM : Confidence.LOW;
        Quality q = tested
                ? new Quality(model, conf, score.origins(), trainingDays, score.dailyWape(), score.week1Error(), score.month1Error(), score.bias())
                : new Quality(model, Confidence.LOW, 0, trainingDays, -1, -1, -1, 0);
        return new Result(id, name, true, note, q, totals, days);
    }

    /** Never promise a range tighter than a floor, whatever a small backtest happened to show. */
    private static double[] tight(double[] band, double minLow, double minHigh) {
        return new double[]{Math.min(minLow, band[0]), Math.max(minHigh, band[1])};
    }

    private static double sum(double[] a, int n) {
        double s = 0;
        for (int i = 0; i < Math.min(n, a.length); i++) s += a[i];
        return s;
    }

    private static List<String> eventsOn(LocalDate d, FestivalCalendar cal) {
        List<String> out = new ArrayList<>();
        for (FestivalCalendar.Hit h : cal.on(d)) {
            out.add(switch (h.phase()) {
                case RUN_UP -> h.festival().name() + " run-up";
                case DAY -> h.festival().name();
                case AFTER -> "After " + h.festival().name();
            });
        }
        return out;
    }

    // ---- festival impact over the forecast days

    private List<FestivalImpact> festivalImpacts(List<Chosen> usable, LocalDate trainedThrough, LocalDate through) {
        Map<String, double[]> acc = new LinkedHashMap<>(); // key -> {impact, baseline}
        Map<String, FestivalCalendar.Occurrence> occ = new LinkedHashMap<>();
        Map<String, boolean[]> learned = new LinkedHashMap<>();
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (Chosen c : usable) {
            for (LocalDate d = trainedThrough.plusDays(1); !d.isAfter(through); d = d.plusDays(1)) {
                List<Trainer.FestivalEffect> effects = c.model.festivalEffects(d);
                if (effects.isEmpty()) continue;
                double with = c.model.expected(d);
                double without = c.model.expectedWithoutFestivals(d);
                double share = (with - without) / effects.size();
                for (Trainer.FestivalEffect e : effects) {
                    String key = e.festivalId() + "@" + e.mainDay();
                    occ.putIfAbsent(key, new FestivalCalendar.Occurrence(calendar.festival(e.festivalId()), e.mainDay()));
                    double[] a = acc.computeIfAbsent(key, k -> new double[2]);
                    a[0] += share;
                    a[1] += without / effects.size();
                    learned.computeIfAbsent(key, k -> new boolean[1])[0] |= e.learned();
                    seen.merge(key, e.timesSeen(), Math::max);
                }
            }
        }
        List<FestivalImpact> out = new ArrayList<>();
        for (Map.Entry<String, double[]> e : acc.entrySet()) {
            FestivalCalendar.Occurrence o = occ.get(e.getKey());
            double base = e.getValue()[1];
            out.add(new FestivalImpact(o.festival().id(), o.festival().name(), o.date(), e.getValue()[0], base > 0 ? e.getValue()[0] / base : 0,
                    learned.get(e.getKey())[0], seen.getOrDefault(e.getKey(), 0)));
        }
        out.sort(Comparator.comparing(FestivalImpact::date));
        return out;
    }

    /** Days between two dates, for callers sizing a forecast window. */
    public static long daysBetween(LocalDate a, LocalDate b) {
        return ChronoUnit.DAYS.between(a, b);
    }
}
