package com.salonplatform.forecast;

import java.time.LocalDate;

/** How often a branch actually trades on each weekday, so a branch closed on Mondays is forecast at zero on Mondays. */
final class OpenDays {

    static final int WINDOW_DAYS = 84;
    private static final double CLOSED_BELOW = 0.15;

    private OpenDays() {}

    /** Share of recent days with any sale, per weekday (Monday = 0). A weekday that is almost never open counts as closed. */
    static double[] rates(Series s, LocalDate trainEnd) {
        LocalDate from = trainEnd.minusDays(WINDOW_DAYS - 1L);
        if (from.isBefore(s.start())) from = s.start();
        int[] open = new int[7];
        int[] all = new int[7];
        for (LocalDate d = from; !d.isAfter(trainEnd); d = d.plusDays(1)) {
            int dow = d.getDayOfWeek().getValue() - 1;
            all[dow]++;
            if (s.at(d) > 0) open[dow]++;
        }
        double[] rate = new double[7];
        for (int i = 0; i < 7; i++) {
            if (all[i] < 3) {
                rate[i] = 1.0;
                continue;
            }
            double r = open[i] / (double) all[i];
            rate[i] = r < CLOSED_BELOW ? 0 : r;
        }
        return rate;
    }
}
