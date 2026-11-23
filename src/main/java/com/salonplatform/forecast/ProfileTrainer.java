package com.salonplatform.forecast;

import java.time.LocalDate;

/**
 * The plain baseline: how much a branch sells on a typical Monday, Tuesday, and so on, from the last eight weeks.
 * It knows nothing about trend, season or festivals, which makes it a fair yardstick for the richer model.
 */
final class ProfileTrainer implements Trainer {

    static final int LEVEL_DAYS = 56;
    /** Shrinks a weekday's average toward the overall average, so one odd Tuesday does not set the pattern. */
    private static final double PRIOR_DAYS = 2.0;

    @Override
    public String name() {
        return "weekday-profile";
    }

    @Override
    public int complexity() {
        return 0;
    }

    @Override
    public Model fit(Series s, LocalDate trainEnd) {
        double[] open = OpenDays.rates(s, trainEnd);
        LocalDate from = trainEnd.minusDays(LEVEL_DAYS - 1L);
        if (from.isBefore(s.start())) from = s.start();
        double[] sum = new double[7];
        int[] n = new int[7];
        double total = 0;
        int count = 0;
        for (LocalDate d = from; !d.isAfter(trainEnd); d = d.plusDays(1)) {
            double v = s.at(d);
            if (v <= 0) continue;
            int dow = d.getDayOfWeek().getValue() - 1;
            sum[dow] += v;
            n[dow]++;
            total += v;
            count++;
        }
        double overall = count == 0 ? 0 : total / count;
        double[] mean = new double[7];
        for (int i = 0; i < 7; i++) mean[i] = (sum[i] + PRIOR_DAYS * overall) / (n[i] + PRIOR_DAYS);
        return day -> open[day.getDayOfWeek().getValue() - 1] * mean[day.getDayOfWeek().getValue() - 1];
    }
}
