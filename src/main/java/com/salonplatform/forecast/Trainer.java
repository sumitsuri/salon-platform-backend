package com.salonplatform.forecast;

import java.time.LocalDate;
import java.util.List;

/** A way of learning a branch's sales pattern from its history up to a date, and of predicting later days. */
interface Trainer {

    /** What a trainer learned: an expected revenue for any day after the training date. */
    interface Model {
        /** Expected revenue on {@code day}, counting the chance the branch is closed. */
        double expected(LocalDate day);

        /** Expected revenue with the festival calendar left out, to measure what festivals add. */
        default double expectedWithoutFestivals(LocalDate day) {
            return expected(day);
        }

        /** What the festival calendar adds to a day, as a multiplier (1.0 when nothing applies). */
        default double festivalFactor(LocalDate day) {
            return 1.0;
        }

        /** The festival effects behind {@link #festivalFactor}, for explaining a forecast. */
        default List<FestivalEffect> festivalEffects(LocalDate day) {
            return List.of();
        }
    }

    /** How one festival phase changes a day, and whether that comes from the brand's data or from an assumption. */
    record FestivalEffect(String festivalId, String name, FestivalCalendar.Phase phase, LocalDate mainDay, double change, boolean learned,
                          int timesSeen) {}

    String name();

    /** Complexity rank: when two trainers score alike, the simpler one wins. */
    int complexity();

    Model fit(Series series, LocalDate trainEnd);
}
