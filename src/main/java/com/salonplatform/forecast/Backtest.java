package com.salonplatform.forecast;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Honest accuracy: pretend it is an earlier date, train on what was known then, predict the next days, and compare with what
 * really happened. Repeating this at several earlier dates ("rolling origin") shows how the model does on days it never saw.
 */
final class Backtest {

    static final int HORIZON = 30;
    static final int STEP_DAYS = 14;
    static final int MAX_ORIGINS = 8;
    /** A branch needs this many days of history at an origin before a forecast made from it is judged. */
    static final int MIN_TRAIN_DAYS = 56;

    private Backtest() {}

    /** One pretend forecast: what happened and what the model predicted for the {@link #HORIZON} days after {@code origin}. */
    record Run(LocalDate origin, double[] actual, double[] predicted, boolean trained) {}

    /** Origins shared by every branch, newest last, all at least {@link #HORIZON} days before the end of the data. */
    static List<LocalDate> origins(LocalDate lastDay) {
        List<LocalDate> o = new ArrayList<>();
        LocalDate d = lastDay.minusDays(HORIZON);
        for (int i = 0; i < MAX_ORIGINS; i++) {
            o.add(0, d);
            d = d.minusDays(STEP_DAYS);
        }
        return o;
    }

    static List<Run> run(Series s, Trainer trainer, List<LocalDate> origins) {
        List<Run> runs = new ArrayList<>();
        for (LocalDate origin : origins) {
            double[] actual = new double[HORIZON];
            double[] predicted = new double[HORIZON];
            for (int k = 0; k < HORIZON; k++) actual[k] = s.at(origin.plusDays(k + 1L));
            long history = java.time.temporal.ChronoUnit.DAYS.between(s.start(), origin) + 1;
            boolean trained = history >= MIN_TRAIN_DAYS;
            if (trained) {
                Trainer.Model m = trainer.fit(s, origin);
                for (int k = 0; k < HORIZON; k++) predicted[k] = m.expected(origin.plusDays(k + 1L));
            }
            runs.add(new Run(origin, actual, predicted, trained));
        }
        return runs;
    }

    /** Error summaries for one trainer on one series (or a sum of series). */
    record Score(int origins, double dailyWape, double week1Error, double month1Error, double bias,
                 double[] dailyRatio, double[] weekRatio, double[] monthRatio) {

        /** Blended error used to rank trainers: totals matter most to an owner, daily accuracy less. */
        double blended() {
            if (origins == 0) return Double.MAX_VALUE;
            return 0.4 * month1Error + 0.3 * week1Error + 0.3 * dailyWape;
        }
    }

    static Score score(List<Run> runs) {
        int used = 0;
        double absErr = 0, actualSum = 0, predSum = 0;
        List<Double> weekErrs = new ArrayList<>();
        List<Double> monthErrs = new ArrayList<>();
        List<Double> daily = new ArrayList<>();
        List<Double> weekly = new ArrayList<>();
        List<Double> monthly = new ArrayList<>();
        for (Run r : runs) {
            if (!r.trained()) continue;
            used++;
            double a30 = 0, p30 = 0;
            for (int k = 0; k < HORIZON; k++) {
                absErr += Math.abs(r.actual()[k] - r.predicted()[k]);
                actualSum += r.actual()[k];
                predSum += r.predicted()[k];
                a30 += r.actual()[k];
                p30 += r.predicted()[k];
                if (r.actual()[k] > 0 && r.predicted()[k] > 0) daily.add(r.actual()[k] / r.predicted()[k]);
            }
            if (a30 > 0 && p30 > 0) {
                monthErrs.add(Math.abs(a30 - p30) / a30);
                monthly.add(a30 / p30);
            }
            for (int w = 0; w + 7 <= HORIZON; w += 7) {
                double a = 0, p = 0;
                for (int k = w; k < w + 7; k++) {
                    a += r.actual()[k];
                    p += r.predicted()[k];
                }
                if (a > 0) {
                    weekErrs.add(Math.abs(a - p) / a);
                    if (p > 0) weekly.add(a / p);
                }
            }
        }
        if (used == 0 || actualSum <= 0) return new Score(0, 1, 1, 1, 0, null, null, null);
        // each period's error is capped at 100%, so one freak period (a closure, a launch month) cannot make the model look hopeless
        return new Score(used, absErr / actualSum, cappedMean(weekErrs, 1), cappedMean(monthErrs, 1), predSum / actualSum - 1,
                band(daily), band(weekly), band(monthly));
    }

    private static double cappedMean(List<Double> v, double fallback) {
        if (v.isEmpty()) return fallback;
        return v.stream().mapToDouble(x -> Math.min(1.0, x)).average().orElse(fallback);
    }

    /** P10 and P90 of actual/predicted ratios, or null when there are too few to say. */
    static double[] band(List<Double> ratios) {
        if (ratios.size() < 5) return null;
        double[] a = ratios.stream().mapToDouble(Double::doubleValue).toArray();
        Arrays.sort(a);
        return new double[]{quantile(a, 0.10), quantile(a, 0.90)};
    }

    static double quantile(double[] sorted, double q) {
        double pos = q * (sorted.length - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo);
    }

    /** Adds several branches' runs day by day, for a brand-wide view of the same pretend forecasts. */
    static List<Run> sum(List<List<Run>> perBranch) {
        List<Run> total = new ArrayList<>();
        int n = perBranch.get(0).size();
        for (int i = 0; i < n; i++) {
            double[] a = new double[HORIZON];
            double[] p = new double[HORIZON];
            boolean any = false;
            for (List<Run> runs : perBranch) {
                Run r = runs.get(i);
                if (!r.trained()) continue;
                any = true;
                for (int k = 0; k < HORIZON; k++) {
                    a[k] += r.actual()[k];
                    p[k] += r.predicted()[k];
                }
            }
            total.add(new Run(perBranch.get(0).get(i).origin(), a, p, any));
        }
        return total;
    }
}
