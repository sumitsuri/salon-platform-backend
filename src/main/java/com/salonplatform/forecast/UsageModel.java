package com.salonplatform.forecast;

import java.util.Arrays;

/**
 * How much of one product gets used per service performed, learned from past stock movements.
 *
 * <p>Nobody records "this facial used 15 g of cream", but stock usage is logged and the services done each week are known, so
 * the rate can be recovered by regression: weekly usage against weekly services in each category, with rates that cannot be
 * negative. A single average rate per service is kept as the fallback, and the better of the two on the most recent weeks is
 * used. When there is too little usage history the product is marked as unknown rather than guessed.
 */
public final class UsageModel {

    public enum Mode { NONE, POOLED, BY_CATEGORY }

    public static final int MIN_WEEKS = 8;

    private final Mode mode;
    private final double[] rates;
    private final double basePerWeek;
    private final double holdoutError;
    private final int weeks;

    private UsageModel(Mode mode, double[] rates, double basePerWeek, double holdoutError, int weeks) {
        this.mode = mode;
        this.rates = rates;
        this.basePerWeek = basePerWeek;
        this.holdoutError = holdoutError;
        this.weeks = weeks;
    }

    public Mode mode() {
        return mode;
    }

    public double holdoutError() {
        return holdoutError;
    }

    public int weeks() {
        return weeks;
    }

    public double[] rates() {
        return rates.clone();
    }

    public double basePerWeek() {
        return basePerWeek;
    }

    /** Expected usage for the given number of services per category over {@code days} days. */
    public double need(double[] servicesByCategory, double days) {
        if (mode == Mode.NONE) return 0;
        double total = basePerWeek * days / 7.0;
        for (int k = 0; k < rates.length && k < servicesByCategory.length; k++) total += rates[k] * servicesByCategory[k];
        return total;
    }

    /** How trustworthy the rate is, from how well it predicted weeks it had not seen. */
    public ForecastEngine.Confidence confidence() {
        if (mode == Mode.NONE) return ForecastEngine.Confidence.INSUFFICIENT;
        return holdoutError <= 0.25 ? ForecastEngine.Confidence.HIGH : holdoutError <= 0.45 ? ForecastEngine.Confidence.MEDIUM : ForecastEngine.Confidence.LOW;
    }

    static UsageModel none(int weeks) {
        return new UsageModel(Mode.NONE, new double[0], 0, 1, weeks);
    }

    /**
     * @param services rows (weeks, oldest first) by category: how many services were done
     * @param usage    the product used in each of those weeks
     */
    public static UsageModel fit(double[][] services, double[] usage) {
        int n = usage.length;
        if (n < MIN_WEEKS || Arrays.stream(usage).sum() <= 0) return none(n);
        int k = services[0].length;
        int split = Math.max(MIN_WEEKS / 2, (int) Math.floor(n * 0.75));
        if (n - split < 3) split = n - 3;

        double pooledOnTrain = pooledRate(services, usage, 0, split);
        double[] pooledRates = new double[k];
        Arrays.fill(pooledRates, pooledOnTrain);
        double pooledError = holdout(services, usage, split, pooledRates, 0);

        double[] cat = nnls(services, usage, 0, split);
        double catError = holdout(services, usage, split, Arrays.copyOf(cat, k), cat[k]);

        boolean byCategory = catError < pooledError * 0.95;
        if (byCategory) {
            double[] all = nnls(services, usage, 0, n);
            return new UsageModel(Mode.BY_CATEGORY, Arrays.copyOf(all, k), all[k], catError, n);
        }
        double rate = pooledRate(services, usage, 0, n);
        double[] r = new double[k];
        Arrays.fill(r, rate);
        return new UsageModel(Mode.POOLED, r, 0, pooledError, n);
    }

    private static double pooledRate(double[][] services, double[] usage, int from, int to) {
        double u = 0, s = 0;
        for (int i = from; i < to; i++) {
            u += usage[i];
            for (double v : services[i]) s += v;
        }
        return s <= 0 ? 0 : u / s;
    }

    /** Error (as a fraction of what was used) on the weeks from {@code from} on. */
    private static double holdout(double[][] services, double[] usage, int from, double[] rates, double base) {
        double err = 0, used = 0;
        for (int i = from; i < usage.length; i++) {
            double p = base;
            for (int j = 0; j < rates.length; j++) p += rates[j] * services[i][j];
            err += Math.abs(usage[i] - p);
            used += usage[i];
        }
        return used <= 0 ? 1 : err / used;
    }

    /** Non-negative least squares by coordinate descent; the last coefficient is a per-week base (also non-negative). */
    static double[] nnls(double[][] services, double[] usage, int from, int to) {
        int m = to - from;
        int k = services[0].length;
        double[][] a = new double[m][k + 1];
        double[] y = new double[m];
        for (int i = 0; i < m; i++) {
            System.arraycopy(services[from + i], 0, a[i], 0, k);
            a[i][k] = 1;
            y[i] = usage[from + i];
        }
        double[] colNorm = new double[k + 1];
        for (int j = 0; j <= k; j++) for (int i = 0; i < m; i++) colNorm[j] += a[i][j] * a[i][j];
        double[] x = new double[k + 1];
        double[] resid = y.clone();
        for (int iter = 0; iter < 800; iter++) {
            double change = 0;
            for (int j = 0; j <= k; j++) {
                if (colNorm[j] <= 0) continue;
                double dot = 0;
                for (int i = 0; i < m; i++) dot += a[i][j] * resid[i];
                double next = Math.max(0, x[j] + dot / colNorm[j]);
                double d = next - x[j];
                if (d != 0) {
                    for (int i = 0; i < m; i++) resid[i] -= a[i][j] * d;
                    x[j] = next;
                    change = Math.max(change, Math.abs(d));
                }
            }
            if (change < 1e-9) break;
        }
        return x;
    }
}
