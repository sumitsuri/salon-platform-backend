package com.salonplatform.demand;

import com.salonplatform.dto.demand.DemandRecommendation;
import com.salonplatform.dto.demand.MonthStat;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class DemandAnalyzerTest {

    /** Builds a 7×24 grid from f(dayIndex 0=Mon, hour). */
    static double[][] grid(BiFunction<Integer, Integer, Double> f) {
        double[][] g = new double[7][24];
        for (int d = 0; d < 7; d++) for (int h = 0; h < 24; h++) g[d][h] = f.apply(d, h);
        return g;
    }

    static int[] weeks(int n) {
        int[] occ = new int[7];
        java.util.Arrays.fill(occ, n);
        return occ;
    }

    /** Per-average-day arrivals × weeks = period totals. */
    static double[][] total(double[][] perDay, int weeks) {
        double[][] t = new double[7][24];
        for (int d = 0; d < 7; d++) for (int h = 0; h < 24; h++) t[d][h] = perDay[d][h] * weeks;
        return t;
    }

    static boolean open(int h) {
        return h >= 10 && h < 20;
    }

    /** A salon open 10-8 where weekday mornings are dead, evenings and weekends are busy. */
    static double[][] demandPerDay() {
        return grid((d, h) -> {
            if (!open(h)) return 0.0;
            boolean weekend = d >= 5;
            if (weekend) return h >= 16 ? 14.0 : 3.0;
            if (h < 12) return 2.0;
            if (h >= 17) return 10.0;
            return 3.0;
        });
    }

    static DemandAnalyzer.Input input(double[][] perDay, double[][] staffPerHour, boolean staffAvailable, List<MonthStat> months,
                                      double[][] cancelledPerDay, long last28, long prev28) {
        return input(perDay, staffPerHour, staffAvailable, months, cancelledPerDay, last28, prev28, 60.0);
    }

    static DemandAnalyzer.Input input(double[][] perDay, double[][] staffPerHour, boolean staffAvailable, List<MonthStat> months,
                                      double[][] cancelledPerDay, long last28, long prev28, double visitMinutes) {
        int w = 13;
        double[][] staffMinutes = staffPerHour == null ? new double[7][24] : total(grid((d, h) -> staffPerHour[d][h] * 60.0), w);
        double[][] revenue = total(grid((d, h) -> perDay[d][h] * 700.0), w);
        return new DemandAnalyzer.Input(10, 20, weeks(w), total(perDay, w),
                cancelledPerDay == null ? new double[7][24] : total(cancelledPerDay, w),
                revenue, staffMinutes, staffAvailable, 150.0, months, last28, prev28, visitMinutes);
    }

    static double[][] fixedShift(double people) {
        return grid((d, h) -> open(h) ? people : 0.0);
    }

    static List<String> types(DemandAnalyzer.Output out) {
        return out.recommendations().stream().map(DemandRecommendation::getType).toList();
    }

    @Test
    void fixedShiftSalonGetsShiftAdviceAndRushCover() {
        DemandAnalyzer.Output out = DemandAnalyzer.analyze(input(demandPerDay(), fixedShift(5), true, null, null, 0, 0));

        assertTrue(out.enoughData());
        assertEquals(0.7, out.customersPerStaffHour());
        assertNotNull(out.summary().getStaffingFitPercent());
        assertEquals(60.0, out.summary().getVisitMinutes());

        // Weekday mornings are dead while evenings are stretched → move starts later, don't hire.
        DemandRecommendation stagger = out.recommendations().stream()
                .filter(r -> r.getType().equals("STAGGER_START")).findFirst().orElseThrow();
        assertEquals(10, stagger.getStartHour());
        assertEquals(12, stagger.getEndHour());
        assertEquals(List.of(1, 2, 3, 4, 5), stagger.getDays());
        assertTrue(stagger.getTitle().contains("weekdays"), stagger.getTitle());
        assertTrue(stagger.getWhatToDo().contains("10 am to 12 pm"), stagger.getWhatToDo());

        // Weekend evenings and weekday evenings are over-loaded.
        List<DemandRecommendation> rush = out.recommendations().stream().filter(r -> r.getType().equals("RUSH")).toList();
        assertFalse(rush.isEmpty());
        assertTrue(rush.stream().anyMatch(r -> r.getDays().contains(6) && r.getStartHour() >= 16), rush.toString());

        assertEquals("Sat 4-5 pm".substring(0, 3), out.summary().getBusiestSlot().substring(0, 3));
        assertEquals(14.0, out.summary().getBusiestSlotVisitsPerHour());
        assertTrue(out.summary().getIdleStaffCostPerMonth() == null || out.summary().getIdleStaffCostPerMonth() > 0);
        assertTrue(out.recommendations().size() <= DemandAnalyzer.MAX_RECOMMENDATIONS);
    }

    @Test
    void matchedStaffingEarnsFewComplaints() {
        // Staff sized so everyone is busy about 65% of the time → nothing stretched, nothing idle.
        double[][] demand = demandPerDay();
        double[][] staff = grid((d, h) -> open(h) ? Math.max(1.0, demand[d][h] / 0.65) : 0.0);
        DemandAnalyzer.Output out = DemandAnalyzer.analyze(input(demand, staff, true, null, null, 0, 0));
        assertTrue(out.enoughData());
        assertEquals(100, out.summary().getStaffingFitPercent());
        assertTrue(out.summary().getTeamBusyPercent() >= 60 && out.summary().getTeamBusyPercent() <= 70, "busy " + out.summary().getTeamBusyPercent());
        assertEquals(0.0, out.summary().getStretchedHoursPerWeek());
        assertFalse(types(out).contains("RUSH"));
        assertFalse(types(out).contains("STAGGER_START"));
        assertFalse(types(out).contains("QUIET"));
    }

    @Test
    void withoutAttendanceItFallsBackToDemandAlone() {
        DemandAnalyzer.Output out = DemandAnalyzer.analyze(input(demandPerDay(), null, false, null, null, 0, 0));
        assertTrue(out.enoughData());
        assertNull(out.customersPerStaffHour());
        assertNull(out.summary().getStaffingFitPercent());
        assertNull(out.summary().getSpareStaffHoursPerWeek());
        List<String> t = types(out);
        assertTrue(t.contains("RUSH") || t.contains("STAGGER_START"), t.toString());
        DemandRecommendation rush = out.recommendations().stream().filter(r -> r.getType().equals("RUSH")).findFirst().orElseThrow();
        assertTrue(rush.getWhatWeSee().contains("Turn on attendance"), rush.getWhatWeSee());
    }

    @Test
    void tooLittleHistoryGivesNoAdvice() {
        double[][] tiny = grid((d, h) -> open(h) && d == 0 ? 0.2 : 0.0);
        DemandAnalyzer.Output out = DemandAnalyzer.analyze(input(tiny, null, false, null, null, 0, 0));
        assertFalse(out.enoughData());
        assertTrue(out.recommendations().isEmpty());
        assertNotNull(out.dataNote());

        DemandAnalyzer.Output none = DemandAnalyzer.analyze(input(new double[7][24], null, false, null, null, 0, 0));
        assertFalse(none.enoughData());
        assertTrue(none.dataNote().contains("No customer visits"));
    }

    @Test
    void closedWeekdaysAreNamedAndExcludedFromAverages() {
        double[][] demand = grid((d, h) -> open(h) && d != 0 ? 6.0 : 0.0);
        DemandAnalyzer.Output out = DemandAnalyzer.analyze(input(demand, null, false, null, null, 0, 0));
        assertTrue(out.enoughData());
        assertEquals("Mondays", out.summary().getClosedDays());
        assertEquals(6, out.summary().getOpenDays());
        assertEquals(60.0, out.summary().getAvgVisitsPerOpenDay());
    }

    @Test
    void seasonalSwingsAreNamedFromCompleteMonthsOnly() {
        List<MonthStat> months = new ArrayList<>();
        double[] perDay = {20, 20, 20, 20, 20, 12, 12, 20, 20, 30, 34, 36};
        String[] names = {"2025-11", "2025-12", "2026-01", "2026-02", "2026-03", "2026-04", "2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10"};
        for (int i = 0; i < 12; i++) {
            months.add(new MonthStat(names[i], (long) (perDay[i] * 30), perDay[i] * 30 * 700, 30, perDay[i], i == 11));
        }
        DemandAnalyzer.Output out = DemandAnalyzer.analyze(input(demandPerDay(), fixedShift(5), true, months, null, 0, 0));
        DemandRecommendation season = out.recommendations().stream().filter(r -> r.getType().equals("SEASON")).findFirst().orElseThrow();
        assertTrue(season.getWhatWeSee().contains("Busiest: Sep"), season.getWhatWeSee());
        assertTrue(season.getWhatWeSee().contains("Quietest: Apr (−42%), May (−42%)")
                || season.getWhatWeSee().contains("Quietest: May (−42%), Apr (−42%)"), season.getWhatWeSee());
        // The partial October is ignored, so it must not be called out as the peak.
        assertFalse(season.getWhatWeSee().contains("Oct"), season.getWhatWeSee());
    }

    @Test
    void shortHistoryDoesNotInventSeasons() {
        List<MonthStat> months = List.of(
                new MonthStat("2026-08", 600, 400000, 30, 20, false),
                new MonthStat("2026-09", 900, 600000, 30, 30, false));
        DemandAnalyzer.Output out = DemandAnalyzer.analyze(input(demandPerDay(), fixedShift(5), true, months, null, 0, 0));
        assertFalse(types(out).contains("SEASON"));
    }

    @Test
    void cancellationHotspotIsFlagged() {
        double[][] cancelled = grid((d, h) -> open(h) ? (h == 18 ? 3.0 : 0.2) : 0.0);
        DemandAnalyzer.Output out = DemandAnalyzer.analyze(input(demandPerDay(), fixedShift(5), true, null, cancelled, 0, 0));
        DemandRecommendation c = out.recommendations().stream().filter(r -> r.getType().equals("CANCELLATIONS")).findFirst()
                .orElseThrow(() -> new AssertionError(out.recommendations().stream().map(x -> x.getType() + "/" + x.getPriority()).toList().toString()));
        assertEquals(18, c.getStartHour());
        assertEquals(19, c.getEndHour());
    }

    @Test
    void visitsOutsideOpeningHoursAreCalledOut() {
        double[][] demand = grid((d, h) -> open(h) ? 6.0 : (h == 9 ? 3.0 : 0.0));
        DemandAnalyzer.Output out = DemandAnalyzer.analyze(input(demand, null, false, null, null, 0, 0));
        assertTrue(types(out).contains("OFF_HOURS"), types(out).toString());
        assertTrue(out.summary().getOutsideHoursPercent() > 3);
    }

    @Test
    void trendNeedsEnoughVisitsAndAMeaningfulChange() {
        assertFalse(types(DemandAnalyzer.analyze(input(demandPerDay(), fixedShift(5), true, null, null, 400, 395))).contains("TREND"));
        assertFalse(types(DemandAnalyzer.analyze(input(demandPerDay(), fixedShift(5), true, null, null, 10, 5))).contains("TREND"));
        DemandAnalyzer.Output down = DemandAnalyzer.analyze(input(demandPerDay(), fixedShift(5), true, null, null, 300, 400));
        DemandRecommendation t = down.recommendations().stream().filter(r -> r.getType().equals("TREND")).findFirst().orElseThrow();
        assertTrue(t.getTitle().contains("down 25%"), t.getTitle());
        assertEquals(-25.0, down.summary().getTrendPercent());
    }

    @Test
    void weekendsBeingBusierIsExplainedAsADayMix() {
        double[][] demand = grid((d, h) -> open(h) ? (d >= 5 ? 12.0 : 4.0) : 0.0);
        DemandAnalyzer.Output out = DemandAnalyzer.analyze(input(demand, null, false, null, null, 0, 0));
        DemandRecommendation mix = out.recommendations().stream().filter(r -> r.getType().equals("DAY_MIX")).findFirst().orElseThrow();
        assertTrue(mix.getTitle().contains("3× busier") || mix.getTitle().contains("3.0× busier"), mix.getTitle());
        assertEquals(List.of(6, 7), mix.getDays());
        assertEquals(3.0, out.summary().getDayPeakRatio());
    }

    @Test
    void rushCoverIsSizedFromHowBusyPeopleAre() {
        // 4 customers an hour, 60-minute visits → 4 staff-hours needed each hour; 3 people is 133% busy.
        // To run at ~70% you need 4/0.7 = 5.7 → 6 people, i.e. 3 more.
        double[][] demand = grid((d, h) -> open(h) ? (h >= 17 ? 4.0 : 1.5) : 0.0);
        DemandAnalyzer.Output out = DemandAnalyzer.analyze(input(demand, fixedShift(3), true, null, null, 0, 0));
        DemandRecommendation rush = out.recommendations().stream().filter(r -> r.getType().equals("RUSH")).findFirst().orElseThrow();
        assertEquals(17, rush.getStartHour());
        assertEquals(20, rush.getEndHour());
        assertTrue(rush.getTitle().contains("every day 5-8 pm"), rush.getTitle());
        assertTrue(rush.getWhatToDo().contains("Add 3 more people"), rush.getWhatToDo());
        assertTrue(rush.getWhatWeSee().contains("busy 133% of the time"), rush.getWhatWeSee());
    }

    @Test
    void shorterVisitsMeanLessStretch() {
        double[][] demand = demandPerDay();
        double fast = DemandAnalyzer.analyze(input(demand, fixedShift(5), true, null, null, 0, 0, 20.0))
                .summary().getStretchedHoursPerWeek();
        double slow = DemandAnalyzer.analyze(input(demand, fixedShift(5), true, null, null, 0, 0, 90.0))
                .summary().getStretchedHoursPerWeek();
        assertTrue(fast < slow, fast + " vs " + slow);
    }

    @Test
    void anOverstaffedBranchIsToldToTrimNotToHire() {
        // 8 people on the floor for ~2 customers an hour: busy about 25% of the time.
        double[][] demand = grid((d, h) -> open(h) ? 2.0 + (h >= 17 ? 1.0 : 0.0) : 0.0);
        DemandAnalyzer.Output out = DemandAnalyzer.analyze(input(demand, fixedShift(8), true, null, null, 0, 0));
        assertFalse(types(out).contains("RUSH"), types(out).toString());
        DemandRecommendation over = out.recommendations().stream().filter(r -> r.getType().equals("OVERSTAFFED")).findFirst().orElseThrow();
        assertEquals("HIGH", over.getPriority());
        assertTrue(over.getTitle().startsWith("Your team is busy only"), over.getTitle());
        assertTrue(over.getWhatToDo().contains("Attendance counts everyone"), over.getWhatToDo());
        assertTrue(out.summary().getTeamBusyPercent() < 40);
        assertTrue(out.summary().getSpareStaffHoursPerWeek() > 20);
        assertNotNull(out.summary().getIdleStaffCostPerMonth());
    }

    @Test
    void moneyAndNumbersReadNaturally() {
        assertEquals("1,23,456", DemandAnalyzer.inr(123456));
        assertEquals("999", DemandAnalyzer.inr(999));
        assertEquals("12,000", DemandAnalyzer.inr(12000));
        assertEquals("5", DemandAnalyzer.fmt(5.04));
        assertEquals("5.4", DemandAnalyzer.fmt(5.36));
    }
}
