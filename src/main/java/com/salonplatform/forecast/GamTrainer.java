package com.salonplatform.forecast;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An additive model of log revenue, fitted per branch to its own history by weighted ridge regression:
 * a level, a gently damped trend, a weekday pattern, a start-and-end-of-month effect, optionally a month-of-year
 * pattern (only with about a year of history) and optionally festival effects.
 *
 * <p>Recent days weigh more than old ones (half-life about five months), so the level follows the business. Festival
 * effects start from the calendar's assumptions (scaled by {@code priorWeight}) and are corrected by whatever the
 * branch's own history shows; a festival the branch has not yet lived through keeps the scaled assumption. Days the
 * branch was shut on a festival are remembered, so it is forecast closed again.
 */
final class GamTrainer implements Trainer {

    static final int MAX_TRAIN_DAYS = 730;
    static final int MIN_DAYS = 28;
    private static final double HALF_LIFE_DAYS = 150;
    private static final double TREND_DAMP_DAYS = 45;
    private static final double MAX_TREND_PER_QUARTER = 0.22;
    private static final int MONTH_HISTORY_DAYS = 330;
    private static final double FESTIVAL_MIN_WEIGHT = 0.7;

    private final String name;
    private final int complexity;
    private final boolean seasons;
    private final boolean festivals;
    private final double priorWeight;
    private final FestivalCalendar calendar;

    GamTrainer(String name, int complexity, boolean seasons, boolean festivals, double priorWeight, FestivalCalendar calendar) {
        this.name = name;
        this.complexity = complexity;
        this.seasons = seasons;
        this.festivals = festivals;
        this.priorWeight = priorWeight;
        this.calendar = calendar;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int complexity() {
        return complexity;
    }

    @Override
    public Model fit(Series s, LocalDate trainEnd) {
        LocalDate from = trainEnd.minusDays(MAX_TRAIN_DAYS - 1L);
        if (from.isBefore(s.start())) from = s.start();
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(trainEnd); d = d.plusDays(1)) {
            if (s.at(d) > 0) days.add(d);
        }
        if (days.size() < MIN_DAYS) return new ProfileTrainer().fit(s, trainEnd);

        boolean useMonths = seasons && ChronoUnit.DAYS.between(from, trainEnd) >= MONTH_HISTORY_DAYS;
        Map<String, Integer> festivalColumns = new LinkedHashMap<>();
        int base = 11 + (useMonths ? 12 : 0);
        if (festivals) {
            for (LocalDate d : days) {
                for (FestivalCalendar.Hit h : calendar.on(d)) festivalColumns.putIfAbsent(h.key(), base + festivalColumns.size());
            }
        }
        Layout layout = new Layout(trainEnd, useMonths, festivalColumns);

        double[] lambda = new double[layout.size];
        lambda[1] = 2;
        for (int i = 2; i <= 8; i++) lambda[i] = 2;
        lambda[9] = 3;
        lambda[10] = 3;
        if (useMonths) for (int i = 11; i < 23; i++) lambda[i] = 12;
        for (int c : festivalColumns.values()) lambda[c] = 3;

        int n = days.size();
        double[][] x = new double[n][];
        double[] y = new double[n];
        double[] w = new double[n];
        for (int i = 0; i < n; i++) {
            LocalDate d = days.get(i);
            x[i] = layout.features(d);
            if (festivals) {
                for (FestivalCalendar.Hit h : calendar.on(d)) x[i][festivalColumns.get(h.key())] = 1;
            }
            y[i] = Math.log(s.at(d)) - offset(d);
            long age = ChronoUnit.DAYS.between(d, trainEnd);
            w[i] = Math.max(0.1, Math.pow(0.5, age / HALF_LIFE_DAYS));
            // A festival does not go stale the way the general level does: last year's Diwali is still the best evidence of Diwali.
            if (festivals && !calendar.on(d).isEmpty()) w[i] = Math.max(w[i], FESTIVAL_MIN_WEIGHT);
        }
        double[] beta = Ridge.solve(x, y, w, lambda);
        beta[1] = Math.max(-MAX_TREND_PER_QUARTER, Math.min(MAX_TREND_PER_QUARTER, beta[1]));

        double num = 0;
        double den = 0;
        for (int i = 0; i < n; i++) {
            double fit = 0;
            for (int j = 0; j < beta.length; j++) fit += x[i][j] * beta[j];
            double r = y[i] - fit;
            num += w[i] * r * r;
            den += w[i];
        }
        double variance = Math.min(0.6, den == 0 ? 0 : num / den);

        double[] open = OpenDays.rates(s, trainEnd);
        Map<String, double[]> closures = festivals ? festivalClosures(s, from, trainEnd, open) : Map.of();
        Map<String, Integer> timesSeen = festivals ? timesSeen(s, from, trainEnd) : Map.of();
        return new Fitted(layout, beta, variance, open, closures, timesSeen, festivalColumns);
    }

    private double offset(LocalDate d) {
        if (!festivals) return 0;
        double o = 0;
        for (FestivalCalendar.Hit h : calendar.on(d)) o += priorWeight * h.festival().prior(h.phase());
        return o;
    }

    /** For each festival, {closed days, observed days} of its main day among weekdays the branch normally trades. */
    private Map<String, double[]> festivalClosures(Series s, LocalDate from, LocalDate to, double[] open) {
        Map<String, double[]> m = new HashMap<>();
        for (FestivalCalendar.Occurrence o : calendar.between(from, to)) {
            if (!s.covers(o.date()) || o.date().isBefore(from)) continue;
            if (open[o.date().getDayOfWeek().getValue() - 1] <= 0) continue;
            double[] c = m.computeIfAbsent(o.festival().id(), k -> new double[2]);
            c[1]++;
            if (s.at(o.date()) <= 0) c[0]++;
        }
        return m;
    }

    private Map<String, Integer> timesSeen(Series s, LocalDate from, LocalDate to) {
        Map<String, Integer> m = new HashMap<>();
        for (FestivalCalendar.Occurrence o : calendar.between(from, to)) {
            if (s.covers(o.date())) m.merge(o.festival().id(), 1, Integer::sum);
        }
        return m;
    }

    /** Where each feature sits in a row. */
    private static final class Layout {
        final LocalDate trainEnd;
        final boolean months;
        final Map<String, Integer> festivalColumns;
        final int size;

        Layout(LocalDate trainEnd, boolean months, Map<String, Integer> festivalColumns) {
            this.trainEnd = trainEnd;
            this.months = months;
            this.festivalColumns = festivalColumns;
            this.size = 11 + (months ? 12 : 0) + festivalColumns.size();
        }

        double trend(LocalDate d) {
            long h = ChronoUnit.DAYS.between(trainEnd, d);
            double t = h <= 0 ? h : TREND_DAMP_DAYS * (1 - Math.exp(-h / TREND_DAMP_DAYS));
            return t / 90.0;
        }

        double[] features(LocalDate d) {
            double[] row = new double[size];
            row[0] = 1;
            row[1] = trend(d);
            row[1 + d.getDayOfWeek().getValue()] = 1;
            int dom = d.getDayOfMonth();
            if (dom <= 5) row[9] = 1;
            if (dom >= 27) row[10] = 1;
            if (months) row[11 + d.getMonthValue() - 1] = 1;
            return row;
        }
    }

    private final class Fitted implements Model {
        private final Layout layout;
        private final double[] beta;
        private final double variance;
        private final double[] open;
        private final Map<String, double[]> closures;
        private final Map<String, Integer> timesSeen;
        private final Map<String, Integer> festivalColumns;

        Fitted(Layout layout, double[] beta, double variance, double[] open, Map<String, double[]> closures,
               Map<String, Integer> timesSeen, Map<String, Integer> festivalColumns) {
            this.layout = layout;
            this.beta = beta;
            this.variance = variance;
            this.open = open;
            this.closures = closures;
            this.timesSeen = timesSeen;
            this.festivalColumns = festivalColumns;
        }

        @Override
        public double expected(LocalDate day) {
            return expected(day, festivals);
        }

        @Override
        public double expectedWithoutFestivals(LocalDate day) {
            return expected(day, false);
        }

        private double expected(LocalDate day, boolean withFestivals) {
            double o = open[day.getDayOfWeek().getValue() - 1];
            if (o <= 0) return 0;
            double[] f = layout.features(day);
            double mu = 0;
            for (int j = 0; j < f.length; j++) if (f[j] != 0) mu += f[j] * beta[j];
            if (withFestivals) {
                mu += offset(day);
                for (FestivalCalendar.Hit h : calendar.on(day)) {
                    Integer c = festivalColumns.get(h.key());
                    if (c != null) mu += beta[c];
                }
            }
            return o * (withFestivals ? closedFactor(day) : 1) * Math.exp(mu + variance / 2);
        }

        private double closedFactor(LocalDate day) {
            if (!festivals) return 1;
            double factor = 1;
            for (FestivalCalendar.Hit h : calendar.on(day)) {
                if (h.phase() != FestivalCalendar.Phase.DAY) continue;
                double[] c = closures.get(h.festival().id());
                if (c != null && c[1] > 0) factor *= 1 - c[0] / c[1];
            }
            return factor;
        }

        @Override
        public double festivalFactor(LocalDate day) {
            if (!festivals) return 1;
            double change = 0;
            for (FestivalEffect e : festivalEffects(day)) change += Math.log1p(e.change());
            return Math.exp(change) * closedFactor(day);
        }

        @Override
        public List<FestivalEffect> festivalEffects(LocalDate day) {
            if (!festivals) return List.of();
            List<FestivalEffect> out = new ArrayList<>();
            for (FestivalCalendar.Hit h : calendar.on(day)) {
                Integer c = festivalColumns.get(h.key());
                double log = priorWeight * h.festival().prior(h.phase()) + (c == null ? 0 : beta[c]);
                int seen = timesSeen.getOrDefault(h.festival().id(), 0);
                if (Math.abs(log) < 0.005 && c == null) continue;
                out.add(new FestivalEffect(h.festival().id(), h.festival().name(), h.phase(), h.mainDay(), Math.expm1(log), c != null && seen > 0, seen));
            }
            return out;
        }
    }
}
