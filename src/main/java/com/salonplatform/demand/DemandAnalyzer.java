package com.salonplatform.demand;

import com.salonplatform.dto.demand.DemandRecommendation;
import com.salonplatform.dto.demand.DemandSummary;
import com.salonplatform.dto.demand.MonthStat;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;

/**
 * Turns a branch's weekday-by-hour demand (and, when available, who was on the floor) into headline numbers and
 * plain-language recommendations. Pure arithmetic over arrays, so it can be tested without a database.
 *
 * <p>Vocabulary: a cell is one weekday + hour. {@code A} = customers arriving per hour on an average such day,
 * {@code S} = staff on the floor, {@code v} = how long a visit takes. The staff time customers need each hour is
 * {@code A × v}, so <b>utilisation</b> = A × v ÷ S is how busy each person is. At 85%+ there is no slack and
 * customers wait (<b>stretched</b>); at 45% or less, and with a person to spare, the cell is <b>spare</b>. Without
 * attendance data the model falls back to comparing each hour with the branch's own average demand.
 */
public final class DemandAnalyzer {

    /** Indexes are 0 = Monday … 6 = Sunday; hours 0-23. */
    public record Input(
            int openHour,
            int closeHour,
            int[] occurrences,
            double[][] visits,
            double[][] cancelled,
            double[][] revenue,
            double[][] staffMinutes,
            boolean staffAvailable,
            Double staffHourCost,
            List<MonthStat> months,
            long visitsLast28,
            long visitsPrev28,
            double visitMinutes) {}

    public record Output(
            DemandSummary summary,
            List<DemandRecommendation> recommendations,
            boolean enoughData,
            String dataNote,
            Double customersPerStaffHour,
            /** [weekday 0=Mon][hour] → state of each open cell (null when there was not enough data). */
            State[][] states,
            /** [weekday][hour] → how busy each person is (1.0 = busy all the time); -1 when attendance is unknown. */
            double[][] busy) {}

    public enum State { STRETCHED, OK, IDLE, NONE }

    private record Win(int day, int start, int end) {}

    private static final class Group {
        final TreeSet<Integer> days = new TreeSet<>();
        int start;
        int end;
    }

    /** Busy this much of the time = no slack left: walk-ins queue, appointments run late. */
    static final double STRETCHED_UTILISATION = 0.85;
    /** At or below this (with someone to spare) the team has clear room. */
    static final double IDLE_UTILISATION = 0.45;
    /** The comfortable level to staff for: busy, but with room for walk-ins and overruns. */
    static final double TARGET_UTILISATION = 0.70;
    static final int MIN_VISITS = 100;
    static final int MIN_OPEN_DAYS_SEEN = 14;
    static final double WEEKS_PER_MONTH = 52.0 / 12.0;
    static final int MAX_RECOMMENDATIONS = 7;

    private DemandAnalyzer() {}

    public static Output analyze(Input in) {
        double[][] visits = in.visits();
        double totalVisits = sum(visits);
        double totalCancelled = sum(in.cancelled());
        double totalRevenue = sum(in.revenue());

        List<Integer> openDays = new ArrayList<>();
        List<Integer> closedDays = new ArrayList<>();
        int daysSeen = 0;
        for (int d = 0; d < 7; d++) {
            if (in.occurrences()[d] <= 0) continue;
            if (rowSum(visits[d]) > 0) {
                openDays.add(d);
                daysSeen += in.occurrences()[d];
            } else if (totalVisits > 0) {
                closedDays.add(d);
            }
        }
        if (totalVisits < MIN_VISITS || daysSeen < MIN_OPEN_DAYS_SEEN) {
            String note = totalVisits == 0
                    ? "No customer visits recorded in this period."
                    : "Only " + (long) totalVisits + " visits over " + daysSeen
                            + " open days so far — advice needs a few weeks of history (about " + MIN_VISITS
                            + " visits) to be reliable.";
            return new Output(emptySummary(in, openDays, closedDays), List.of(), false, note, null, null, null);
        }

        int open = in.openHour();
        int close = in.closeHour();

        double[][] a = new double[7][24];
        double[][] s = new double[7][24];
        double[][] r = new double[7][24];
        for (int d : openDays) {
            double occ = in.occurrences()[d];
            for (int h = 0; h < 24; h++) {
                a[d][h] = visits[d][h] / occ;
                s[d][h] = in.staffMinutes()[d][h] / (60.0 * occ);
                r[d][h] = in.revenue()[d][h] / occ;
            }
        }

        double mu = 0;
        int cellCount = 0;
        for (int d : openDays) {
            for (int h = open; h < close; h++) {
                mu += a[d][h];
                cellCount++;
            }
        }
        mu = cellCount == 0 ? 0 : mu / cellCount;
        if (mu <= 0) {
            return new Output(emptySummary(in, openDays, closedDays), List.of(), false,
                    "Visits were recorded outside opening hours only, so there is nothing to compare.", null, null, null);
        }

        // ── Staffing: how busy is each person? ───────────────────────────────────────────────
        double visitHours = Math.max(0.25, Math.min(3.0, in.visitMinutes() / 60.0));
        boolean[] staffKnown = new boolean[7];
        boolean staffed = false;
        if (in.staffAvailable()) {
            int staffedCells = 0;
            for (int d : openDays) {
                staffKnown[d] = rowSum(in.staffMinutes()[d]) > 0;
                if (!staffKnown[d]) continue;
                for (int h = open; h < close; h++) if (s[d][h] >= 0.5) staffedCells++;
            }
            staffed = staffedCells >= 8;
            if (!staffed) Arrays.fill(staffKnown, false);
        }

        // ── Classify every open weekday-hour ─────────────────────────────────────────────────
        State[][] st = new State[7][24];
        for (State[] row : st) Arrays.fill(row, State.NONE);
        double[][] busyGrid = new double[7][24];
        for (double[] row : busyGrid) Arrays.fill(row, -1);
        double stretchedHours = 0;
        double spareStaffHours = 0;
        double okVisits = 0;
        double knownVisits = 0;
        double demandStaffHours = 0;
        double floorStaffHours = 0;
        for (int d : openDays) {
            for (int h = open; h < close; h++) {
                double av = a[d][h];
                State state;
                if (staffed && staffKnown[d]) {
                    double util = av * visitHours / Math.max(s[d][h], 0.5);
                    busyGrid[d][h] = util;
                    double needed = av * visitHours / TARGET_UTILISATION;
                    if (av >= 1.5 && util >= STRETCHED_UTILISATION) state = State.STRETCHED;
                    else if (util <= IDLE_UTILISATION && Math.floor(s[d][h] - Math.ceil(needed)) >= 1) state = State.IDLE;
                    else state = State.OK;
                    knownVisits += visits[d][h];
                    if (state == State.OK) okVisits += visits[d][h];
                    spareStaffHours += Math.max(0, s[d][h] - needed);
                    demandStaffHours += av * visitHours;
                    floorStaffHours += s[d][h];
                } else {
                    if (av >= 2 && av >= 1.4 * mu) state = State.STRETCHED;
                    else if (av <= 0.5 * mu) state = State.IDLE;
                    else state = State.OK;
                }
                st[d][h] = state;
                if (state == State.STRETCHED) stretchedHours++;
            }
        }

        // ── Headline numbers ─────────────────────────────────────────────────────────────────
        double[] dayAvg = new double[7];
        for (int d : openDays) dayAvg[d] = rowSum(a[d]);
        int busiestDay = argmax(dayAvg, openDays);
        int quietestDay = argmin(dayAvg, openDays);
        double avgPerOpenDay = openDays.stream().mapToDouble(d -> dayAvg[d]).sum() / openDays.size();

        double[] hourAvg = new double[24];
        for (int h = 0; h < 24; h++) {
            for (int d : openDays) hourAvg[h] += a[d][h] / openDays.size();
        }
        int busiestHour = open;
        int quietestHour = open;
        for (int h = open; h < close; h++) {
            if (hourAvg[h] > hourAvg[busiestHour]) busiestHour = h;
            if (hourAvg[h] < hourAvg[quietestHour]) quietestHour = h;
        }
        int bestD = openDays.get(0);
        int bestH = open;
        for (int d : openDays) {
            for (int h = open; h < close; h++) {
                if (a[d][h] > a[bestD][bestH]) {
                    bestD = d;
                    bestH = h;
                }
            }
        }
        double outside = 0;
        for (int d = 0; d < 7; d++) {
            for (int h = 0; h < 24; h++) {
                if (h < open || h >= close) outside += visits[d][h];
            }
        }
        double revenuePerVisit = totalRevenue / totalVisits;
        Double trend = in.visitsPrev28() >= 30
                ? (in.visitsLast28() - in.visitsPrev28()) * 100.0 / in.visitsPrev28() : null;
        Double idleCost = staffed && in.staffHourCost() != null && spareStaffHours > 0
                ? round(spareStaffHours * in.staffHourCost() * WEEKS_PER_MONTH, 100) : null;

        DemandSummary summary = DemandSummary.builder()
                .avgVisitsPerOpenDay(round1(avgPerOpenDay))
                .revenuePerVisit(Math.round(revenuePerVisit))
                .busiestSlot(TimeLabels.dayShort(bestD + 1) + " " + TimeLabels.range(bestH, bestH + 1))
                .busiestSlotVisitsPerHour(round1(a[bestD][bestH]))
                .busiestDay(TimeLabels.dayLong(busiestDay + 1))
                .quietestDay(TimeLabels.dayLong(quietestDay + 1))
                .dayPeakRatio(dayAvg[quietestDay] > 0 ? round1(dayAvg[busiestDay] / dayAvg[quietestDay]) : null)
                .busiestHour(TimeLabels.range(busiestHour, busiestHour + 1))
                .quietestHour(TimeLabels.range(quietestHour, quietestHour + 1))
                .staffingFitPercent(staffed && knownVisits > 0 ? (int) Math.round(okVisits * 100.0 / knownVisits) : null)
                .teamBusyPercent(staffed && floorStaffHours > 0 ? (int) Math.round(demandStaffHours * 100.0 / floorStaffHours) : null)
                .visitMinutes(round1(in.visitMinutes()))
                .stretchedHoursPerWeek(round1(stretchedHours))
                .spareStaffHoursPerWeek(staffed ? round1(spareStaffHours) : null)
                .idleStaffCostPerMonth(idleCost)
                .trendPercent(trend == null ? null : round1(trend))
                .outsideHoursPercent(round1(outside * 100.0 / totalVisits))
                .openDays(openDays.size())
                .closedDays(closedDays.isEmpty() ? "" : TimeLabels.days(closedDays.stream().map(x -> x + 1).toList()))
                .build();

        // ── Recommendations ──────────────────────────────────────────────────────────────────
        Ctx c = new Ctx(in, a, s, r, st, staffKnown, staffed, visitHours, mu, openDays, totalVisits, totalRevenue, revenuePerVisit);
        List<Rec> recs = new ArrayList<>();
        boolean[][] consumed = new boolean[7][24];

        if (staffed && floorStaffHours > 0) {
            addOverstaffed(c, recs, floorStaffHours, demandStaffHours, spareStaffHours, openDays.size() * (close - open));
        }
        addStagger(c, recs, consumed);
        addRush(c, recs);
        addQuiet(c, recs, consumed);
        addPremium(c, recs);
        addDayMix(c, recs, dayAvg);
        addSeason(c, recs);
        addCancellations(c, recs);
        addOutsideHours(c, recs, outside);
        addEdgeHours(c, recs, consumed, hourAvg);
        addTrend(c, recs, trend);

        recs.sort(Comparator.comparingDouble((Rec x) -> x.weight).reversed());
        List<DemandRecommendation> out = new ArrayList<>();
        Map<String, Integer> perType = new HashMap<>();
        for (Rec x : recs) {
            if (out.size() >= MAX_RECOMMENDATIONS) break;
            // Several near-identical cards read as noise: keep the three that matter most per kind.
            int limit = x.type.equals("RUSH") || x.type.equals("QUIET") ? 3 : 2;
            if (perType.merge(x.type, 1, Integer::sum) > limit) continue;
            out.add(x.build());
        }
        return new Output(summary, out, true, null, staffed ? round1(TARGET_UTILISATION / visitHours) : null, st, busyGrid);
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════
    // Recommendation builders
    // ═════════════════════════════════════════════════════════════════════════════════════════

    private record Ctx(
            Input in, double[][] a, double[][] s, double[][] r, State[][] st, boolean[] staffKnown, boolean staffedOverall,
            double visitHours, double mu, List<Integer> openDays, double totalVisits, double totalRevenue, double revenuePerVisit) {
        boolean staffed(int d) { return staffedOverall && staffKnown[d]; }
        /** Staff needed to run these arrivals at the comfortable utilisation. */
        double needed(double arrivals) { return arrivals * visitHours / TARGET_UTILISATION; }
        double util(double arrivals, double staff) { return arrivals * visitHours / Math.max(staff, 0.5); }
        int open() { return in.openHour(); }
        int close() { return in.closeHour(); }
    }

    private static final class Rec {
        String id;
        String type;
        double weight;
        String title;
        String see;
        String todo;
        String impact;
        Double impactAmount;
        List<Integer> days = List.of();
        int start = -1;
        int end = -1;

        DemandRecommendation build() {
            String priority = weight >= 15 ? "HIGH" : weight >= 7 ? "MEDIUM" : "LOW";
            return DemandRecommendation.builder()
                    .id(id).type(type).priority(priority).title(title).whatWeSee(see).whatToDo(todo)
                    .impact(impact).impactAmount(impactAmount)
                    .days(days).startHour(start).endHour(end).build();
        }
    }

    /** The whole week is under-used: say so once, plainly, instead of as many separate quiet spells. */
    private static void addOverstaffed(Ctx c, List<Rec> recs, double floorHours, double demandHours, double spareHours, int cells) {
        double busy = demandHours / floorHours;
        if (busy > IDLE_UTILISATION || cells == 0) return;
        double avgOnFloor = floorHours / cells;
        double neededPerHour = (demandHours / TARGET_UTILISATION) / cells;
        double avgArrivals = demandHours / c.visitHours / cells;
        int plan = Math.max(1, (int) Math.ceil(neededPerHour));
        Rec x = new Rec();
        x.type = "OVERSTAFFED";
        x.id = "OVERSTAFFED";
        x.title = "Your team is busy only " + pct(busy) + "% of the time";
        x.see = "In an average opening hour about " + fmt(avgArrivals) + " customers arrive and " + fmt(avgOnFloor)
                + " people are on the floor. A typical visit takes " + Math.round(c.visitHours * 60)
                + " minutes, so customers need only about " + fmt(neededPerHour) + " people's time even with comfortable breathing room.";
        x.todo = "Plan around " + plan + " people in a typical hour instead of " + fmt(avgOnFloor) + ", and add cover only for the rush hours. "
                + "Use staggered starts and weekly offs rather than one fixed shift for everyone. "
                + "(Attendance counts everyone who clocks in — if reception or managers are included, the real picture is a bit better than this.)";
        if (c.in.staffHourCost() != null && spareHours > 0) {
            double month = round(spareHours * c.in.staffHourCost() * WEEKS_PER_MONTH, 100);
            x.impact = "About " + fmt(spareHours) + " paid staff-hours a week go beyond what customers need — roughly ₹" + inr(month)
                    + " a month at your average salary (estimate).";
            x.impactAmount = month;
        } else {
            x.impact = "About " + fmt(spareHours) + " paid staff-hours a week go beyond what customers need.";
        }
        x.weight = busy <= 0.35 ? 40 : 30;
        recs.add(x);
    }

    /** Quiet first hours + a rush later (or a quiet close + an earlier rush): move shifts instead of adding people. */
    private static void addStagger(Ctx c, List<Rec> recs, boolean[][] consumed) {
        List<Win> startRuns = new ArrayList<>();
        List<Win> endRuns = new ArrayList<>();
        for (int d : c.openDays) {
            int e = c.open();
            while (e < c.close() && c.st[d][e] == State.IDLE) e++;
            if (e - c.open() >= 2 && e < c.close() && anyStretched(c, d, e, c.close())) {
                startRuns.add(new Win(d, c.open(), e));
            }
            int b = c.close();
            while (b > c.open() && c.st[d][b - 1] == State.IDLE) b--;
            if (c.close() - b >= 2 && b > c.open() && anyStretched(c, d, c.open(), b)) {
                endRuns.add(new Win(d, b, c.close()));
            }
        }
        for (Group g : group(startRuns, 2)) {
            emitStagger(c, recs, consumed, g, true);
        }
        for (Group g : group(endRuns, 2)) {
            emitStagger(c, recs, consumed, g, false);
        }
    }

    private static void emitStagger(Ctx c, List<Rec> recs, boolean[][] consumed, Group g, boolean atStart) {
        double aSum = 0, sSum = 0;
        int n = 0;
        double spareStaff = 0;
        for (int d : g.days) {
            for (int h = g.start; h < g.end; h++) {
                aSum += c.a[d][h];
                sSum += c.s[d][h];
                n++;
                if (c.staffed(d)) spareStaff += Math.max(0, c.s[d][h] - c.needed(c.a[d][h]));
                consumed[d][h] = true;
            }
        }
        double avgA = aSum / n;
        double avgS = sSum / n;
        double sparePerHour = c.staffed(g.days.first()) ? spareStaff / n : 0;
        int people = c.staffed(g.days.first()) ? Math.max(1, (int) Math.floor(sparePerHour)) : 1;
        double spareHoursWeek = spareStaff;
        // The rush these people are needed for: the stretched hours on the other side of the day.
        int rushStart = Integer.MAX_VALUE, rushEnd = -1;
        for (int d : g.days) {
            for (int h = atStart ? g.end : c.open(); h < (atStart ? c.close() : g.start); h++) {
                if (c.st[d][h] == State.STRETCHED) {
                    rushStart = Math.min(rushStart, h);
                    rushEnd = Math.max(rushEnd, h + 1);
                }
            }
        }
        String days = TimeLabels.days(iso(g.days));
        String quiet = TimeLabels.range(g.start, g.end);
        String rush = rushEnd > 0 ? TimeLabels.range(rushStart, rushEnd) : "later in the day";
        Rec x = new Rec();
        x.type = atStart ? "STAGGER_START" : "EARLY_LEAVE";
        x.id = x.type + "-" + key(g.days) + "-" + g.start + "-" + g.end;
        x.title = atStart
                ? "Start " + people + " " + people(people) + " later on " + days
                : "Let " + people + " " + people(people) + " leave earlier on " + days;
        x.see = c.staffed(g.days.first())
                ? "On " + days + ", " + quiet + " is quiet — about " + fmt(avgA) + " customers an hour with " + fmt(avgS)
                        + " staff on the floor, so the team is busy only " + pct(c.util(avgA, avgS)) + "% of the time — while "
                        + rush + " is stretched."
                : "On " + days + ", " + quiet + " is quiet — about " + fmt(avgA) + " customers an hour, well under your usual "
                        + fmt(c.mu) + " — while " + rush + " is stretched.";
        x.todo = atStart
                ? "Move " + people + " of the team's start from " + TimeLabels.hour(g.start) + " to " + TimeLabels.hour(g.end)
                        + " so they overlap the " + rush + " rush, instead of adding a new person."
                : "Let " + people + " " + people(people) + " finish at " + TimeLabels.hour(g.start)
                        + " and cover the " + rush + " rush earlier in the day instead.";
        if (spareHoursWeek > 0 && c.in.staffHourCost() != null) {
            double month = round(spareHoursWeek * c.in.staffHourCost() * WEEKS_PER_MONTH, 100);
            x.impact = "Frees about " + fmt(spareHoursWeek) + " staff-hours a week (≈ ₹" + inr(month)
                    + " a month of paid time) and puts them where customers are waiting. Estimate from average salary.";
            x.impactAmount = month;
        } else if (spareHoursWeek > 0) {
            x.impact = "Frees about " + fmt(spareHoursWeek) + " staff-hours a week and puts them where customers are waiting.";
        } else {
            x.impact = "Puts your people where customers are, with no extra hiring.";
        }
        x.weight = 12 + Math.min(20, spareHoursWeek * 1.5) + g.days.size();
        x.days = new ArrayList<>(g.days.stream().map(d -> d + 1).toList());
        x.start = g.start;
        x.end = g.end;
        recs.add(x);
    }

    private static boolean anyStretched(Ctx c, int d, int from, int to) {
        for (int h = from; h < to; h++) if (c.st[d][h] == State.STRETCHED) return true;
        return false;
    }

    private static void addRush(Ctx c, List<Rec> recs) {
        List<Win> wins = new ArrayList<>();
        for (int d : c.openDays) wins.addAll(runs(c.st[d], d, State.STRETCHED, 1, c.open(), c.close()));
        for (Group g : group(wins, 1)) {
            double aSum = 0, sSum = 0, rev = 0, extraSum = 0;
            int n = 0;
            for (int d : g.days) {
                for (int h = g.start; h < g.end; h++) {
                    aSum += c.a[d][h];
                    sSum += c.s[d][h];
                    rev += c.r[d][h];
                    if (c.staffed(d)) extraSum += Math.max(0, c.needed(c.a[d][h]) - c.s[d][h]);
                    n++;
                }
            }
            double avgA = aSum / n;
            double avgS = sSum / n;
            boolean staffed = c.staffed(g.days.first());
            int extra = staffed ? Math.max(1, (int) Math.round(extraSum / n)) : 1;
            String days = TimeLabels.days(iso(g.days));
            String range = TimeLabels.range(g.start, g.end);
            double share = c.totalRevenue <= 0 ? 0 : 100.0 * (rev * 1.0) / weeklyRevenue(c);
            Rec x = new Rec();
            x.type = "RUSH";
            x.id = "RUSH-" + key(g.days) + "-" + g.start + "-" + g.end;
            x.title = "Add cover on " + days + " " + range;
            if (staffed) {
                x.see = "About " + fmt(avgA) + " customers arrive every hour and only " + fmt(avgS) + " staff are on the floor — "
                        + "each person is busy " + pct(c.util(avgA, avgS)) + "% of the time, leaving no slack for walk-ins or overruns.";
                x.todo = "Add " + extra + " more " + people(extra) + " for these hours: shift someone's start/finish, bring in a part-timer, "
                        + "and avoid giving leave then. Encourage online booking for the quieter hours either side.";
            } else {
                x.see = "About " + fmt(avgA) + " customers arrive every hour — " + fmt(avgA / c.mu) + "× this branch's usual "
                        + fmt(c.mu) + ". (Turn on attendance to see how many staff are on the floor.)";
                x.todo = "Make sure your strongest team is on for these hours, avoid leave then, and offer online booking for the quieter hours either side.";
            }
            x.impact = "About ₹" + inr(round(rev, 100)) + " a week flows through these hours (" + Math.round(share)
                    + "% of the branch's billing) — long waits here cost the most.";
            x.weight = Math.min(40, share) + (staffed ? 5 : 0) + g.days.size() * 0.5;
            x.days = new ArrayList<>(g.days.stream().map(d -> d + 1).toList());
            x.start = g.start;
            x.end = g.end;
            recs.add(x);
        }
    }

    private static double weeklyRevenue(Ctx c) {
        double w = 0;
        for (int d : c.openDays) for (int h = 0; h < 24; h++) w += c.r[d][h];
        return Math.max(w, 1);
    }

    private static void addQuiet(Ctx c, List<Rec> recs, boolean[][] consumed) {
        List<Win> wins = new ArrayList<>();
        for (int d : c.openDays) {
            for (Win w : runs(c.st[d], d, State.IDLE, 2, c.open(), c.close())) {
                boolean used = false;
                for (int h = w.start(); h < w.end(); h++) used |= consumed[d][h];
                if (!used) wins.add(w);
            }
        }
        for (Group g : group(wins, 2)) {
            double aSum = 0, sSum = 0, spare = 0, gap = 0;
            int n = 0;
            for (int d : g.days) {
                for (int h = g.start; h < g.end; h++) {
                    aSum += c.a[d][h];
                    sSum += c.s[d][h];
                    if (c.staffed(d)) spare += Math.max(0, c.s[d][h] - c.needed(c.a[d][h]));
                    gap += Math.max(0, 0.7 * c.mu - c.a[d][h]);
                    n++;
                }
            }
            double avgA = aSum / n;
            double avgS = sSum / n;
            boolean staffed = c.staffed(g.days.first());
            String days = TimeLabels.days(iso(g.days));
            String range = TimeLabels.range(g.start, g.end);
            Rec x = new Rec();
            x.type = "QUIET";
            x.id = "QUIET-" + key(g.days) + "-" + g.start + "-" + g.end;
            x.title = "Quiet spell: " + days + " " + range;
            x.see = staffed
                    ? "Only " + fmt(avgA) + " customers an hour, yet " + fmt(avgS) + " staff on the floor — the team is busy "
                            + pct(c.util(avgA, avgS)) + "% of the time."
                    : "Only " + fmt(avgA) + " customers an hour — " + Math.round(100 * avgA / c.mu)
                            + "% of this branch's usual " + fmt(c.mu) + ".";
            double extraVisitsWeek = 0.5 * gap;
            double promoMonth = extraVisitsWeek * c.revenuePerVisit * 0.8 * WEEKS_PER_MONTH;
            x.todo = "Stagger lunch and breaks here and use the time for training, stock or deep-cleaning. Test an off-peak offer "
                    + "(say 20% off " + range + " on " + days + ") to pull customers into these hours.";
            StringBuilder impact = new StringBuilder();
            if (extraVisitsWeek >= 1) {
                double rounded = round(promoMonth, 100);
                impact.append("If a 20%-off offer fills half the gap to a normal hour, that is about ")
                        .append(Math.round(extraVisitsWeek)).append(" extra visits a week ≈ ₹").append(inr(rounded))
                        .append(" a month after the discount (average bill ₹").append(inr(c.revenuePerVisit)).append(").");
                x.impactAmount = rounded;
            }
            if (staffed && spare > 0 && c.in.staffHourCost() != null) {
                double month = round(spare * c.in.staffHourCost() * WEEKS_PER_MONTH, 100);
                if (impact.length() > 0) impact.append(' ');
                impact.append("Idle staff time here ≈ ₹").append(inr(month)).append(" a month (estimate from average salary).");
            }
            x.impact = impact.length() == 0 ? null : impact.toString();
            x.weight = 4 + Math.min(14, (staffed ? spare * 1.2 : 0) + g.days.size() * 1.5 + extraVisitsWeek * 0.3);
            x.days = new ArrayList<>(g.days.stream().map(d -> d + 1).toList());
            x.start = g.start;
            x.end = g.end;
            recs.add(x);
        }
    }

    private static void addPremium(Ctx c, List<Rec> recs) {
        double[] hv = new double[24];
        double[] hr = new double[24];
        for (int d : c.openDays) {
            for (int h = 0; h < 24; h++) {
                hv[h] += c.in.visits()[d][h];
                hr[h] += c.in.revenue()[d][h];
            }
        }
        double overall = c.revenuePerVisit;
        if (overall <= 0) return;
        int bestStart = -1, bestEnd = -1;
        double bestTicket = 0;
        int h = c.open();
        while (h < c.close()) {
            if (hv[h] >= 30 && hr[h] / hv[h] >= 1.25 * overall) {
                int e = h;
                double v = 0, rv = 0;
                while (e < c.close() && hv[e] >= 30 && hr[e] / hv[e] >= 1.25 * overall) {
                    v += hv[e];
                    rv += hr[e];
                    e++;
                }
                if (e - h >= 2 && v >= 0.08 * c.totalVisits && rv / v > bestTicket) {
                    bestTicket = rv / v;
                    bestStart = h;
                    bestEnd = e;
                }
                h = e;
            } else {
                h++;
            }
        }
        if (bestStart < 0) return;
        Rec x = new Rec();
        x.type = "PREMIUM_HOURS";
        x.id = "PREMIUM-" + bestStart + "-" + bestEnd;
        x.title = "Your best-paying customers arrive " + TimeLabels.range(bestStart, bestEnd);
        x.see = "Customers arriving then spend about ₹" + inr(bestTicket) + " a visit — " + Math.round((bestTicket / overall - 1) * 100)
                + "% more than the branch average of ₹" + inr(overall) + ".";
        x.todo = "Keep your most experienced stylists and best upsellers on the floor in these hours, avoid giving leave then, "
                + "and put premium services (packages, memberships) front and centre.";
        x.impact = "Every customer lost to a wait or a weaker pitch in these hours costs more than elsewhere in the day.";
        x.weight = 8;
        x.start = bestStart;
        x.end = bestEnd;
        recs.add(x);
    }

    private static void addDayMix(Ctx c, List<Rec> recs, double[] dayAvg) {
        if (c.openDays.size() < 5) return;
        double mean = c.openDays.stream().mapToDouble(d -> dayAvg[d]).sum() / c.openDays.size();
        int hi = argmax(dayAvg, c.openDays);
        int lo = argmin(dayAvg, c.openDays);
        if (dayAvg[lo] <= 0 || dayAvg[hi] / dayAvg[lo] < 1.5) return;
        List<Integer> busy = new ArrayList<>();
        List<Integer> slow = new ArrayList<>();
        for (int d : c.openDays) {
            if (dayAvg[d] >= 1.2 * mean) busy.add(d + 1);
            if (dayAvg[d] <= 0.85 * mean) slow.add(d + 1);
        }
        if (busy.isEmpty() || slow.isEmpty()) return;
        Rec x = new Rec();
        x.type = "DAY_MIX";
        x.id = "DAYMIX";
        x.title = TimeLabels.dayLong(hi + 1) + "s are " + fmt(dayAvg[hi] / dayAvg[lo]) + "× busier than " + TimeLabels.dayLong(lo + 1) + "s";
        x.see = "On average " + fmt(dayAvg[hi]) + " customers arrive on " + TimeLabels.dayLong(hi + 1) + "s against "
                + fmt(dayAvg[lo]) + " on " + TimeLabels.dayLong(lo + 1) + "s.";
        x.todo = "Roster by day, not by habit: give weekly offs and approve leave on " + TimeLabels.days(slow)
                + ", keep everyone on for " + TimeLabels.days(busy) + ", and schedule training or maintenance on the slow days.";
        x.impact = "Matching headcount to the day avoids both overtime on busy days and idle pay on slow ones.";
        x.weight = Math.min(16, 5 + (dayAvg[hi] / dayAvg[lo] - 1.5) * 6);
        x.days = busy;
        recs.add(x);
    }

    private static void addSeason(Ctx c, List<Rec> recs) {
        List<MonthStat> months = c.in.months() == null ? List.of() : c.in.months().stream().filter(m -> !m.isPartial()).toList();
        if (months.size() < 6) return;
        double mean = months.stream().mapToDouble(MonthStat::getVisitsPerDay).average().orElse(0);
        if (mean <= 0) return;
        List<MonthStat> peak = months.stream().filter(m -> m.getVisitsPerDay() >= 1.15 * mean)
                .sorted(Comparator.comparingDouble(MonthStat::getVisitsPerDay).reversed()).limit(3).toList();
        List<MonthStat> slow = months.stream().filter(m -> m.getVisitsPerDay() <= 0.85 * mean)
                .sorted(Comparator.comparingDouble(MonthStat::getVisitsPerDay)).limit(3).toList();
        if (peak.isEmpty() && slow.isEmpty()) return;
        Rec x = new Rec();
        x.type = "SEASON";
        x.id = "SEASON";
        x.title = "Plan hiring and leave around your busy and slow months";
        StringBuilder see = new StringBuilder();
        if (!peak.isEmpty()) see.append("Busiest: ").append(monthList(peak, mean, true)).append(". ");
        if (!slow.isEmpty()) see.append("Quietest: ").append(monthList(slow, mean, false)).append(".");
        x.see = see.toString().trim();
        x.todo = (peak.isEmpty() ? "" : "Block leave and book extra hands for the busy months. ")
                + (slow.isEmpty() ? "" : "Use the slow months for staff leave, training, deep-cleaning and promotions.");
        x.impact = "Seasonal swings are predictable — rostering for them early avoids last-minute hiring and idle weeks.";
        x.weight = 8 + Math.min(6, (peak.isEmpty() ? 0 : peak.get(0).getVisitsPerDay() / mean - 1) * 10);
        recs.add(x);
    }

    private static String monthList(List<MonthStat> list, double mean, boolean up) {
        List<String> parts = new ArrayList<>();
        for (MonthStat m : list) {
            int month = Integer.parseInt(m.getMonth().substring(5, 7));
            long pct = Math.round(Math.abs(m.getVisitsPerDay() / mean - 1) * 100);
            parts.add(Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " (" + (up ? "+" : "−") + pct + "%)");
        }
        return String.join(", ", parts);
    }

    private static void addCancellations(Ctx c, List<Rec> recs) {
        double[] hv = new double[24];
        double[] hc = new double[24];
        double cancelledTotal = 0, bookedTotal = 0;
        for (int d : c.openDays) {
            for (int h = 0; h < 24; h++) {
                hv[h] += c.in.visits()[d][h];
                hc[h] += c.in.cancelled()[d][h];
            }
        }
        for (int h = 0; h < 24; h++) {
            cancelledTotal += hc[h];
            bookedTotal += hv[h] + hc[h];
        }
        if (bookedTotal <= 0) return;
        double avgRate = cancelledTotal / bookedTotal;
        int bestStart = -1, bestEnd = -1;
        double bestRate = 0;
        int h = c.open();
        while (h < c.close()) {
            double rate = hv[h] + hc[h] > 0 ? hc[h] / (hv[h] + hc[h]) : 0;
            if (hc[h] >= 8 && rate >= 2 * avgRate && rate >= 0.10) {
                int e = h;
                double cs = 0, bs = 0;
                while (e < c.close() && hc[e] >= 8 && (hv[e] + hc[e] > 0) && hc[e] / (hv[e] + hc[e]) >= 2 * avgRate && hc[e] / (hv[e] + hc[e]) >= 0.10) {
                    cs += hc[e];
                    bs += hv[e] + hc[e];
                    e++;
                }
                if (cs / bs > bestRate) {
                    bestRate = cs / bs;
                    bestStart = h;
                    bestEnd = e;
                }
                h = e;
            } else {
                h++;
            }
        }
        if (bestStart < 0) return;
        Rec x = new Rec();
        x.type = "CANCELLATIONS";
        x.id = "CANCEL-" + bestStart + "-" + bestEnd;
        x.title = "Slots at " + TimeLabels.range(bestStart, bestEnd) + " get cancelled a lot";
        x.see = Math.round(bestRate * 100) + "% of bookings in these hours are cancelled, against " + Math.round(avgRate * 100) + "% across the day.";
        x.todo = "Send a reminder a few hours before, confirm by WhatsApp, or ask for a small advance on these slots — and keep a walk-in friendly stylist free to fill gaps.";
        x.impact = "Each cancelled slot in your busiest hours is a chair left empty.";
        x.weight = 6 + Math.min(6, (bestRate - avgRate) * 20);
        x.start = bestStart;
        x.end = bestEnd;
        recs.add(x);
    }

    private static void addOutsideHours(Ctx c, List<Rec> recs, double outside) {
        double pct = outside * 100.0 / c.totalVisits;
        if (pct < 3) return;
        Rec x = new Rec();
        x.type = "OFF_HOURS";
        x.id = "OFFHOURS";
        x.title = Math.round(pct) + "% of visits are logged outside opening hours";
        x.see = "About " + Math.round(outside) + " visits fall before " + TimeLabels.hour(c.open()) + " or after "
                + TimeLabels.hour(c.close()) + ". Either customers really do come then, or walk-ins are being entered late.";
        x.todo = "If they are real, consider opening or closing a bit later. If not, check that walk-ins are entered when the customer arrives and that the till device clock is right — it affects every number on this page.";
        x.impact = null;
        x.weight = Math.min(12, 3 + pct);
        recs.add(x);
    }

    private static void addEdgeHours(Ctx c, List<Rec> recs, boolean[][] consumed, double[] hourAvg) {
        double total = 0;
        for (int h = c.open(); h < c.close(); h++) total += hourAvg[h];
        if (total <= 0 || c.openDays.size() < 5) return;
        int first = c.open();
        int last = c.close() - 1;
        double firstShare = hourAvg[first] / total;
        double lastShare = hourAvg[last] / total;
        boolean firstCovered = false, lastCovered = false;
        for (int d : c.openDays) {
            firstCovered |= consumed[d][first];
            lastCovered |= consumed[d][last];
        }
        if (firstShare < 0.02 && !firstCovered) {
            Rec x = new Rec();
            x.type = "EDGE_HOUR";
            x.id = "EDGE-OPEN";
            x.title = "The first hour (" + TimeLabels.range(first, first + 1) + ") is almost empty";
            x.see = "Only " + fmt(firstShare * 100) + "% of the day's customers arrive in the opening hour.";
            x.todo = "Open an hour later, or keep just one person for set-up and let the rest start when customers do.";
            x.impact = "Saves a full hour of staff time on every open day.";
            x.weight = 6;
            x.start = first;
            x.end = first + 1;
            recs.add(x);
        }
        if (lastShare < 0.02 && !lastCovered) {
            Rec x = new Rec();
            x.type = "EDGE_HOUR";
            x.id = "EDGE-CLOSE";
            x.title = "The last hour (" + TimeLabels.range(last, last + 1) + ") is almost empty";
            x.see = "Only " + fmt(lastShare * 100) + "% of the day's customers arrive in the final hour.";
            x.todo = "Close an hour earlier, or run with a skeleton team for the last hour.";
            x.impact = "Saves a full hour of staff time on every open day.";
            x.weight = 6;
            x.start = last;
            x.end = last + 1;
            recs.add(x);
        }
    }

    private static void addTrend(Ctx c, List<Rec> recs, Double trend) {
        if (trend == null || Math.abs(trend) < 15) return;
        Rec x = new Rec();
        x.type = "TREND";
        x.id = "TREND";
        boolean up = trend > 0;
        x.title = "Footfall is " + (up ? "up " : "down ") + Math.round(Math.abs(trend)) + "% on the previous four weeks";
        x.see = "Customers over the last 28 days " + (up ? "rose" : "fell") + " " + Math.round(Math.abs(trend))
                + "% compared with the 28 days before.";
        x.todo = up
                ? "Check the busiest hours above are covered, and keep whatever you changed (offers, reviews, new services)."
                : "Look at what changed — a stylist leaving, a lapse in offers or reviews, or a seasonal dip — and consider a win-back campaign to past customers.";
        x.impact = null;
        x.weight = Math.min(14, 5 + Math.abs(trend) / 5);
        recs.add(x);
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════════════════════════════

    private static DemandSummary emptySummary(Input in, List<Integer> openDays, List<Integer> closedDays) {
        return DemandSummary.builder()
                .openDays(openDays.size())
                .closedDays(closedDays.isEmpty() ? "" : TimeLabels.days(closedDays.stream().map(x -> x + 1).toList()))
                .build();
    }

    private static List<Win> runs(State[] row, int day, State target, int minLen, int open, int close) {
        List<Win> out = new ArrayList<>();
        int h = open;
        while (h < close) {
            if (row[h] == target) {
                int e = h;
                while (e < close && row[e] == target) e++;
                if (e - h >= minLen) out.add(new Win(day, h, e));
                h = e;
            } else {
                h++;
            }
        }
        return out;
    }

    /** Merges the same pattern seen on several weekdays ("Tue-Thu 2-4 pm"); a group's hours are those in every member. */
    private static List<Group> group(List<Win> wins, int minLen) {
        List<Win> sorted = new ArrayList<>(wins);
        sorted.sort(Comparator.comparingInt(Win::start).thenComparingInt(Win::end).thenComparingInt(Win::day));
        List<Group> groups = new ArrayList<>();
        for (Win w : sorted) {
            Group target = null;
            for (Group g : groups) {
                int overlap = Math.min(g.end, w.end()) - Math.max(g.start, w.start());
                int shorter = Math.min(g.end - g.start, w.end() - w.start());
                if (overlap >= minLen && overlap >= 0.5 * shorter) {
                    target = g;
                    break;
                }
            }
            if (target == null) {
                Group g = new Group();
                g.start = w.start();
                g.end = w.end();
                g.days.add(w.day());
                groups.add(g);
            } else {
                target.start = Math.max(target.start, w.start());
                target.end = Math.min(target.end, w.end());
                target.days.add(w.day());
            }
        }
        return groups;
    }

    /** Internal weekday indexes are 0 = Monday; the label helpers speak ISO (1 = Monday). */
    private static List<Integer> iso(Collection<Integer> days) {
        List<Integer> out = new ArrayList<>();
        for (int d : days) out.add(d + 1);
        return out;
    }

    private static String key(Collection<Integer> days) {
        StringBuilder sb = new StringBuilder();
        for (int d : new TreeSet<>(days)) sb.append(d + 1);
        return sb.toString();
    }

    private static long pct(double fraction) {
        return Math.round(fraction * 100);
    }

    private static String people(int n) {
        return n == 1 ? "person" : "people";
    }

    private static int argmax(double[] v, List<Integer> idx) {
        int best = idx.get(0);
        for (int i : idx) if (v[i] > v[best]) best = i;
        return best;
    }

    private static int argmin(double[] v, List<Integer> idx) {
        int best = idx.get(0);
        for (int i : idx) if (v[i] < v[best]) best = i;
        return best;
    }

    private static double sum(double[][] m) {
        double t = 0;
        for (double[] row : m) t += rowSum(row);
        return t;
    }

    private static double rowSum(double[] row) {
        double t = 0;
        for (double v : row) t += v;
        return t;
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round(double v, double step) {
        return Math.round(v / step) * step;
    }

    /** 5 → "5", 5.4 → "5.4", 12.04 → "12". */
    static String fmt(double v) {
        double r = Math.round(v * 10.0) / 10.0;
        return r == Math.rint(r) ? String.valueOf((long) r) : String.valueOf(r);
    }

    /** Indian digit grouping without the rupee sign. */
    static String inr(double v) {
        long n = Math.round(v);
        String s = String.valueOf(Math.abs(n));
        if (s.length() > 3) {
            String last3 = s.substring(s.length() - 3);
            String rest = s.substring(0, s.length() - 3);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < rest.length(); i++) {
                if (i > 0 && (rest.length() - i) % 2 == 0) sb.append(',');
                sb.append(rest.charAt(i));
            }
            s = sb + "," + last3;
        }
        return (n < 0 ? "-" : "") + s;
    }
}
