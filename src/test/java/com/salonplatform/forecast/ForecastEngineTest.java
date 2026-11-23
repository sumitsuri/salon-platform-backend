package com.salonplatform.forecast;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

class ForecastEngineTest {

    private static final FestivalCalendar CAL = FestivalCalendar.standard();
    private static final double[] DOW = {0.8, 0.85, 0.9, 1.0, 1.15, 1.45, 1.35};

    /** A branch with a known pattern: weekday shape, optional trend, closed days, a festival lift, and noise. */
    private static Series synthetic(String name, LocalDate start, LocalDate end, double base, double growthPerDay, double noise, long seed,
                                    BiFunction<LocalDate, Double, Double> shape) {
        Random rnd = new Random(seed);
        int n = (int) java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        double[] rev = new double[n];
        for (int i = 0; i < n; i++) {
            LocalDate d = start.plusDays(i);
            double v = base * Math.pow(1 + growthPerDay, i) * DOW[d.getDayOfWeek().getValue() - 1] * Math.exp(rnd.nextGaussian() * noise);
            rev[i] = Math.max(0, shape.apply(d, v));
        }
        return new Series(UUID.randomUUID(), name, start, rev);
    }

    @Test
    void ridgeRecoversAKnownRelationship() {
        double[][] x = new double[50][];
        double[] y = new double[50];
        double[] w = new double[50];
        for (int i = 0; i < 50; i++) {
            x[i] = new double[]{1, i / 10.0};
            y[i] = 3 + 2 * (i / 10.0);
            w[i] = 1;
        }
        double[] beta = Ridge.solve(x, y, w, new double[]{0, 0});
        assertThat(beta[0]).isCloseTo(3, org.assertj.core.data.Offset.offset(1e-5));
        assertThat(beta[1]).isCloseTo(2, org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    void theCalendarKnowsTheRunUpAndTheMainDay() {
        LocalDate diwali = LocalDate.of(2026, 11, 8);
        assertThat(CAL.on(diwali).stream().map(FestivalCalendar.Hit::key)).contains("DIWALI:DAY");
        assertThat(CAL.on(diwali.minusDays(3)).stream().map(FestivalCalendar.Hit::key)).contains("DIWALI:RUN_UP");
        assertThat(CAL.on(diwali.minusDays(8)).stream().map(FestivalCalendar.Hit::key)).doesNotContain("DIWALI:RUN_UP");
        assertThat(CAL.on(diwali.plusDays(1)).stream().map(FestivalCalendar.Hit::key)).contains("DIWALI:AFTER");
        assertThat(CAL.coverageEnd()).isAfterOrEqualTo(LocalDate.of(2027, 12, 31));
        assertThat(CAL.between(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30)).stream().map(o -> o.festival().id()))
                .contains("KARVA_CHAUTH", "DIWALI", "BHAI_DOOJ", "DUSSEHRA");
    }

    @Test
    void aBranchClosedOnMondaysIsForecastAtZeroOnMondays() {
        Series s = synthetic("Closed Mondays", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), 40000, 0, 0.08, 1,
                (d, v) -> d.getDayOfWeek() == DayOfWeek.MONDAY ? 0 : v);
        ForecastEngine.Output out = new ForecastEngine(CAL, 0.5).forecast(List.of(s), LocalDate.of(2026, 7, 31));
        ForecastEngine.Result r = out.branches().get(0);
        assertThat(r.forecastable()).isTrue();
        for (ForecastEngine.Day d : r.days()) {
            if (d.date().getDayOfWeek() == DayOfWeek.MONDAY) assertThat(d.expected()).isZero();
            else assertThat(d.expected()).isGreaterThan(0);
        }
    }

    @Test
    void aSteadyBranchIsForecastCloseToItsTrueLevelAndTheRangeHoldsTheTruth() {
        LocalDate start = LocalDate.of(2025, 9, 1);
        LocalDate trainEnd = LocalDate.of(2026, 5, 31);
        Series full = synthetic("Steady", start, LocalDate.of(2026, 7, 31), 40000, 0.0004, 0.12, 7, (d, v) -> v);
        double[] head = java.util.Arrays.copyOf(full.revenue(), (int) java.time.temporal.ChronoUnit.DAYS.between(start, trainEnd) + 1);
        Series history = new Series(full.id(), full.name(), start, head);
        ForecastEngine.Result r = new ForecastEngine(CAL, 0.5).forecast(List.of(history), trainEnd.plusDays(30)).branches().get(0);

        double actual30 = 0;
        for (int i = 1; i <= 30; i++) actual30 += full.at(trainEnd.plusDays(i));
        assertThat(r.totals().next30()).isBetween(actual30 * 0.9, actual30 * 1.1);
        assertThat(actual30).isBetween(r.totals().next30Low(), r.totals().next30High());
        assertThat(r.quality().confidence()).isIn(ForecastEngine.Confidence.HIGH, ForecastEngine.Confidence.MEDIUM);
        assertThat(r.quality().backtests()).isGreaterThanOrEqualTo(6);
    }

    @Test
    void aGrowingBranchIsForecastWhereItsGrowthTakesIt() {
        LocalDate start = LocalDate.of(2025, 10, 1);
        Series s = synthetic("Growing", start, LocalDate.of(2026, 5, 31), 30000, 0.002, 0.08, 3, (d, v) -> v);
        double truth = 0;
        double flat = 0;
        for (int i = 1; i <= 30; i++) {
            LocalDate d = s.end().plusDays(i);
            double dow = DOW[d.getDayOfWeek().getValue() - 1];
            truth += 30000 * Math.pow(1.002, java.time.temporal.ChronoUnit.DAYS.between(start, d)) * dow;
            flat += 30000 * Math.pow(1.002, java.time.temporal.ChronoUnit.DAYS.between(start, s.end())) * dow;
        }
        ForecastEngine.Result r = new ForecastEngine(CAL, 0.5).forecast(List.of(s), s.end().plusDays(30)).branches().get(0);
        assertThat(r.totals().next30()).isBetween(truth * 0.95, truth * 1.05);
        assertThat(r.totals().next30()).isGreaterThan(flat);
    }

    @Test
    void aFestivalLiftSeenLastYearIsLearnedAndAppliedAgain() {
        // two years of data: sales jump 50% in the week before Diwali, and the branch is shut on Diwali itself
        BiFunction<LocalDate, Double, Double> shape = (d, v) -> {
            double out = v;
            for (FestivalCalendar.Hit h : CAL.on(d)) {
                if (h.festival().id().equals("DIWALI") && h.phase() == FestivalCalendar.Phase.RUN_UP) out *= 1.5;
                if (h.festival().id().equals("DIWALI") && h.phase() == FestivalCalendar.Phase.DAY) out = 0;
            }
            return out;
        };
        LocalDate trainEnd = LocalDate.of(2026, 10, 15);
        Series s = synthetic("Festive", LocalDate.of(2024, 9, 1), trainEnd, 30000, 0, 0.08, 11, shape);
        ForecastEngine.Output out = new ForecastEngine(CAL, 0.5).forecast(List.of(s), LocalDate.of(2026, 11, 20));
        ForecastEngine.Result r = out.branches().get(0);
        assertThat(r.quality().model()).contains("festivals");

        LocalDate diwali = LocalDate.of(2026, 11, 8);
        double runUp = r.days().stream().filter(d -> !d.date().isBefore(diwali.minusDays(7)) && d.date().isBefore(diwali)).mapToDouble(ForecastEngine.Day::expected).sum();
        double normal = r.days().stream().filter(d -> !d.date().isBefore(diwali.minusDays(21)) && d.date().isBefore(diwali.minusDays(14))).mapToDouble(ForecastEngine.Day::expected).sum();
        assertThat(runUp / normal).isBetween(1.3, 1.7);
        assertThat(r.days().stream().filter(d -> d.date().equals(diwali)).findFirst().orElseThrow().expected()).isZero();

        ForecastEngine.FestivalImpact impact = out.festivals().stream().filter(f -> f.id().equals("DIWALI")).findFirst().orElseThrow();
        assertThat(impact.learned()).isTrue();
        assertThat(impact.timesSeen()).isEqualTo(2);
    }

    @Test
    void festivalsNeverSeenKeepOnlyTheModestAssumption() {
        Series s = synthetic("New", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 9, 30), 30000, 0, 0.08, 5, (d, v) -> v);
        ForecastEngine.Output out = new ForecastEngine(CAL, 0.5).forecast(List.of(s), LocalDate.of(2026, 11, 30));
        ForecastEngine.FestivalImpact diwali = out.festivals().stream().filter(f -> f.id().equals("DIWALI")).findFirst().orElse(null);
        if (diwali != null) assertThat(diwali.learned()).isFalse();
        // the assumption is scaled down, so even the biggest festival does not swing a brand by more than a few tens of percent
        for (ForecastEngine.FestivalImpact f : out.festivals()) assertThat(Math.abs(f.percent())).isLessThan(0.35);
    }

    @Test
    void tooLittleHistoryIsNotForecastAndShortHistoryIsFlaggedLowConfidence() {
        Series tiny = synthetic("Tiny", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20), 30000, 0, 0.08, 1, (d, v) -> v);
        Series shortish = synthetic("Short", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 20), 30000, 0, 0.08, 2, (d, v) -> v);
        // all series in a call share an end date
        Series tiny20 = new Series(tiny.id(), tiny.name(), tiny.start(), tiny.revenue());
        ForecastEngine.Output out = new ForecastEngine(CAL, 0.5).forecast(List.of(tiny20, shortish), LocalDate.of(2026, 10, 20));
        assertThat(out.branches().get(0).forecastable()).isFalse();
        assertThat(out.branches().get(1).forecastable()).isTrue();
        assertThat(out.branches().get(1).quality().confidence()).isEqualTo(ForecastEngine.Confidence.LOW);
        assertThat(out.brand().forecastable()).isTrue();
        assertThat(out.brand().note()).contains("left out");
    }

    @Test
    void theBrandForecastIsTheSumOfItsBranches() {
        Series a = synthetic("A", LocalDate.of(2025, 10, 1), LocalDate.of(2026, 6, 30), 30000, 0, 0.1, 1, (d, v) -> v);
        Series b = synthetic("B", LocalDate.of(2025, 10, 1), LocalDate.of(2026, 6, 30), 50000, 0, 0.1, 2, (d, v) -> v);
        ForecastEngine.Output out = new ForecastEngine(CAL, 0.5).forecast(List.of(a, b), LocalDate.of(2026, 7, 31));
        for (int i = 0; i < out.brand().days().size(); i++) {
            double sum = out.branches().get(0).days().get(i).expected() + out.branches().get(1).days().get(i).expected();
            assertThat(out.brand().days().get(i).expected()).isCloseTo(sum, org.assertj.core.data.Offset.offset(1e-6));
        }
    }

    @Test
    void theTrickleBeforeABranchReallyOpenedIsDropped() {
        double[] r = new double[100];
        r[3] = 500;
        r[20] = 900;           // two stray early bills
        for (int i = 40; i < 100; i++) r[i] = 20000; // trading starts on day 40
        Series s = new Series(UUID.randomUUID(), "x", LocalDate.of(2026, 1, 1), r);
        Series t = SalesForecastService.launched(s);
        assertThat(t.start()).isEqualTo(LocalDate.of(2026, 1, 1).plusDays(40));
        assertThat(t.length()).isEqualTo(60);
        assertThat(SalesForecastService.launched(new Series(UUID.randomUUID(), "y", s.start(), new double[]{1, 0, 0}))).isNotNull();
    }

    @Test
    void aBranchThatStoppedRecordingSalesIsNotForecast() {
        // busy for four months, then almost nothing for the last five weeks
        Series busy = synthetic("Faded", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), 30000, 0, 0.1, 3, (d, v) -> v);
        double[] r = java.util.Arrays.copyOf(busy.revenue(), busy.length());
        for (int i = r.length - 35; i < r.length; i++) r[i] = i % 17 == 0 ? 2000 : 0;
        Series faded = new Series(busy.id(), busy.name(), busy.start(), r);
        ForecastEngine.Output out = new ForecastEngine(CAL, 0.5).forecast(List.of(faded), LocalDate.of(2026, 7, 31));
        assertThat(out.branches().get(0).forecastable()).isFalse();
        assertThat(out.branches().get(0).note()).contains("of the last 28 days");
        assertThat(out.brand().forecastable()).isFalse();
    }
}
