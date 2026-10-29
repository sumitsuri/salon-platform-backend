package com.salonplatform.service;

import com.salonplatform.demand.DemandAnalyzer;
import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.enums.BranchStatus;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.dto.demand.*;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * "Busy hours": when customers arrive at each branch, by weekday and hour, set against who was on the floor.
 *
 * <p>A customer "arrives" when their walk-in is registered, or at the scheduled time for an online appointment.
 * Cancelled bookings are counted separately and never as visits. Revenue is the invoiced amount of the booking
 * (voided invoices excluded). Staff presence comes from attendance records. Everything is bucketed in India time.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DemandInsightsService {

    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    static final int DEFAULT_DAYS = 90;
    static final int MAX_DAYS = 400;
    /** A branch needs this much attendance in the period before staffing is compared with demand. */
    static final int MIN_ATTENDANCE_RECORDS = 20;
    static final int MIN_ATTENDANCE_DAYS = 7;
    /** Used when a branch has neither recorded nor estimated visit lengths. */
    static final double ASSUMED_VISIT_MINUTES = 45.0;
    /** Recorded durations must be at least this many visits, and between 10 min and 4 h, to be trusted. */
    static final int MIN_RECORDED_DURATIONS = 30;

    private static final String ARRIVALS_CTE = """
            WITH inv AS (
                SELECT booking_id, SUM(grand_total) AS amount
                FROM invoices
                WHERE tenant_id = :tenant AND deleted_at IS NULL
                GROUP BY booking_id
            ), arrivals AS (
                SELECT b.id, b.branch_id, b.status, b.source,
                       CASE WHEN b.source = 'ONLINE' AND b.scheduled_start_at IS NOT NULL
                            THEN b.scheduled_start_at ELSE b.created_at END AS arrived_at
                FROM bookings b
                WHERE b.tenant_id = :tenant AND b.deleted_at IS NULL AND b.status <> 'DRAFT'
                  AND b.branch_id IN (:branches)
            )
            """;

    private final BranchRepository branchRepository;
    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public DemandInsightsResponse getInsights(LocalDate startDate, LocalDate endDate, UUID branchId) {
        SecurityUtils.assertBrandAdmin();
        UUID tenantId = SecurityUtils.requireTenantId();

        LocalDate today = LocalDate.now(ZONE);
        LocalDate end = endDate == null ? today : endDate.isAfter(today) ? today : endDate;
        LocalDate start = startDate == null ? end.minusDays(DEFAULT_DAYS - 1L) : startDate;
        if (start.isAfter(end)) {
            throw new BadRequestException("The start date must be on or before the end date.");
        }
        if (Duration.between(start.atStartOfDay(), end.atStartOfDay()).toDays() + 1 > MAX_DAYS) {
            throw new BadRequestException("Choose a period of at most " + MAX_DAYS + " days.");
        }

        List<Branch> branches;
        if (branchId != null) {
            Branch b = branchRepository.findById(branchId)
                    .filter(x -> tenantId.equals(x.getTenantId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
            branches = List.of(b);
        } else {
            branches = branchRepository.findByTenantIdAndStatus(tenantId, BranchStatus.ACTIVE).stream()
                    .sorted(Comparator.comparing(Branch::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
        if (branches.isEmpty()) {
            return DemandInsightsResponse.builder().startDate(start).endDate(end).timezone(ZONE.getId())
                    .periodDays((int) (end.toEpochDay() - start.toEpochDay() + 1)).branches(List.of()).build();
        }
        List<UUID> ids = branches.stream().map(Branch::getId).toList();

        Instant from = start.atStartOfDay(ZONE).toInstant();
        Instant to = end.plusDays(1).atStartOfDay(ZONE).toInstant();

        Map<UUID, Map<Integer, double[]>> cellRows = loadCells(tenantId, ids, from, to);
        Map<UUID, LocalDate> firstVisit = loadFirstVisit(tenantId, ids);
        Map<UUID, List<MonthStat>> months = loadMonths(tenantId, ids, today, firstVisit);
        Map<UUID, long[]> trend = loadTrend(tenantId, ids, today);
        Map<UUID, double[][]> staffMinutes = new HashMap<>();
        Map<UUID, int[]> attendanceCounts = new HashMap<>();
        loadAttendance(tenantId, branches, start, end, staffMinutes, attendanceCounts);
        Map<UUID, Double> hourlyCost = loadStaffHourCost(tenantId, ids);
        Map<UUID, double[]> visitMinutes = loadVisitMinutes(tenantId, ids, from, to);

        List<BranchDemand> out = new ArrayList<>();
        for (Branch branch : branches) {
            out.add(buildBranch(branch, start, end, firstVisit.get(branch.getId()), cellRows.getOrDefault(branch.getId(), Map.of()),
                    months.getOrDefault(branch.getId(), List.of()), trend.getOrDefault(branch.getId(), new long[] {0, 0}),
                    staffMinutes.get(branch.getId()), attendanceCounts.getOrDefault(branch.getId(), new int[] {0, 0}),
                    hourlyCost.get(branch.getId()), visitMinutes.get(branch.getId())));
        }
        return DemandInsightsResponse.builder()
                .startDate(start).endDate(end).timezone(ZONE.getId())
                .periodDays((int) (end.toEpochDay() - start.toEpochDay() + 1))
                .branches(out)
                .build();
    }

    private BranchDemand buildBranch(Branch branch, LocalDate start, LocalDate end, LocalDate firstVisit,
                                     Map<Integer, double[]> rows, List<MonthStat> months, long[] trend,
                                     double[][] staffMin, int[] attendance, Double hourCost, double[] visitLength) {
        int[] hours = openCloseHours(branch);
        int open = hours[0];
        int close = hours[1];

        // Don't average over days before the branch took its first customer.
        LocalDate effStart = firstVisit != null && firstVisit.isAfter(start) ? firstVisit : start;
        int[] occ = new int[7];
        if (!effStart.isAfter(end)) {
            for (LocalDate d = effStart; !d.isAfter(end); d = d.plusDays(1)) occ[d.getDayOfWeek().getValue() - 1]++;
        }

        double[][] visits = new double[7][24];
        double[][] cancelled = new double[7][24];
        double[][] revenue = new double[7][24];
        double[][] walkIns = new double[7][24];
        double[][] online = new double[7][24];
        for (Map.Entry<Integer, double[]> e : rows.entrySet()) {
            int d = e.getKey() / 24;
            int h = e.getKey() % 24;
            double[] v = e.getValue();
            visits[d][h] = v[0];
            cancelled[d][h] = v[1];
            walkIns[d][h] = v[2];
            online[d][h] = v[3];
            revenue[d][h] = v[4];
        }
        boolean staffAvailable = staffMin != null && attendance[0] >= MIN_ATTENDANCE_RECORDS && attendance[1] >= MIN_ATTENDANCE_DAYS;
        double[][] staff = staffMin == null ? new double[7][24] : staffMin;

        double minutes = visitLength == null ? ASSUMED_VISIT_MINUTES : visitLength[0];
        String minutesSource = visitLength == null ? "assumed" : visitLength[1] == 1 ? "recorded" : "estimated";
        DemandAnalyzer.Output analysis = DemandAnalyzer.analyze(new DemandAnalyzer.Input(
                open, close, occ, visits, cancelled, revenue, staff, staffAvailable, hourCost, months,
                trend[0], trend[1], minutes));

        List<DemandCell> cells = new ArrayList<>();
        long totalVisits = 0;
        long totalCancelled = 0;
        double totalRevenue = 0;
        for (int d = 0; d < 7; d++) {
            for (int h = 0; h < 24; h++) {
                totalVisits += (long) visits[d][h];
                totalCancelled += (long) cancelled[d][h];
                totalRevenue += revenue[d][h];
                if (visits[d][h] > 0 || cancelled[d][h] > 0 || (staffAvailable && staff[d][h] > 0)) {
                    DemandCell cell = new DemandCell(d + 1, h, (long) visits[d][h], (long) cancelled[d][h], (long) walkIns[d][h],
                            (long) online[d][h], Math.round(revenue[d][h]), staffAvailable ? Math.round(staff[d][h]) : 0);
                    if (analysis.states() != null && analysis.states()[d][h] != DemandAnalyzer.State.NONE) {
                        cell.setState(analysis.states()[d][h].name());
                        double busy = analysis.busy()[d][h];
                        if (busy >= 0) cell.setBusyPercent((int) Math.round(busy * 100));
                    }
                    cells.add(cell);
                }
            }
        }
        return BranchDemand.builder()
                .branchId(branch.getId())
                .branchName(branch.getName())
                .openTime(branch.getOpenTime())
                .closeTime(branch.getCloseTime())
                .openHour(open)
                .closeHour(close)
                .totalVisits(totalVisits)
                .totalCancelled(totalCancelled)
                .totalRevenue(Math.round(totalRevenue))
                .dowOccurrences(occ)
                .cells(cells)
                .staffDataAvailable(staffAvailable && analysis.customersPerStaffHour() != null)
                .customersPerStaffHour(analysis.customersPerStaffHour())
                .visitMinutes(Math.round(minutes * 10.0) / 10.0)
                .visitMinutesSource(minutesSource)
                .months(months)
                .enoughData(analysis.enoughData())
                .dataNote(analysis.dataNote())
                .summary(analysis.summary())
                .recommendations(analysis.recommendations())
                .build();
    }

    /** "10:00" / "21:00" → [10, 21]; a closing time with minutes rounds up; bad or missing values fall back to 10-20. */
    static int[] openCloseHours(Branch b) {
        int open = parseHour(b.getOpenTime(), false);
        int close = parseHour(b.getCloseTime(), true);
        if (close == 0) close = 24;
        if (open < 0 || close < 0 || close <= open) return new int[] {10, 20};
        return new int[] {open, Math.min(close, 24)};
    }

    private static int parseHour(String hhmm, boolean roundUp) {
        if (hhmm == null || hhmm.isBlank()) return -1;
        try {
            String[] p = hhmm.trim().split(":");
            int h = Integer.parseInt(p[0]);
            int m = p.length > 1 ? Integer.parseInt(p[1].replaceAll("\\D", "")) : 0;
            if (h < 0 || h > 24) return -1;
            return roundUp && m > 0 ? Math.min(24, h + 1) : h;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    // ── Queries ──────────────────────────────────────────────────────────────────────────────

    /** branch → (dow0 * 24 + hour) → [visits, cancelled, walkIns, online, revenue]. */
    private Map<UUID, Map<Integer, double[]>> loadCells(UUID tenantId, List<UUID> ids, Instant from, Instant to) {
        String sql = ARRIVALS_CTE + """
                SELECT a.branch_id,
                       EXTRACT(ISODOW FROM a.arrived_at AT TIME ZONE 'Asia/Kolkata')::int AS dow,
                       EXTRACT(HOUR FROM a.arrived_at AT TIME ZONE 'Asia/Kolkata')::int AS hr,
                       COUNT(*) FILTER (WHERE a.status <> 'CANCELLED') AS visits,
                       COUNT(*) FILTER (WHERE a.status = 'CANCELLED') AS cancelled,
                       COUNT(*) FILTER (WHERE a.status <> 'CANCELLED' AND a.source = 'WALK_IN') AS walk_ins,
                       COUNT(*) FILTER (WHERE a.status <> 'CANCELLED' AND a.source = 'ONLINE') AS online,
                       COALESCE(SUM(i.amount) FILTER (WHERE a.status <> 'CANCELLED'), 0) AS revenue
                FROM arrivals a
                LEFT JOIN inv i ON i.booking_id = a.id
                WHERE a.arrived_at >= :from AND a.arrived_at < :to
                GROUP BY 1, 2, 3
                """;
        Map<UUID, Map<Integer, double[]>> out = new HashMap<>();
        jdbc.query(sql, params(tenantId, ids).addValue("from", Timestamp.from(from)).addValue("to", Timestamp.from(to)), rs -> {
            UUID branch = rs.getObject("branch_id", UUID.class);
            int dow0 = rs.getInt("dow") - 1;
            int hour = rs.getInt("hr");
            out.computeIfAbsent(branch, k -> new HashMap<>()).put(dow0 * 24 + hour, new double[] {
                    rs.getLong("visits"), rs.getLong("cancelled"), rs.getLong("walk_ins"), rs.getLong("online"),
                    rs.getDouble("revenue")});
        });
        return out;
    }

    private Map<UUID, LocalDate> loadFirstVisit(UUID tenantId, List<UUID> ids) {
        String sql = ARRIVALS_CTE + """
                SELECT a.branch_id, MIN((a.arrived_at AT TIME ZONE 'Asia/Kolkata')::date) AS first_day
                FROM arrivals a WHERE a.status <> 'CANCELLED' GROUP BY 1
                """;
        Map<UUID, LocalDate> out = new HashMap<>();
        jdbc.query(sql, params(tenantId, ids), rs -> {
            out.put(rs.getObject("branch_id", UUID.class), rs.getDate("first_day").toLocalDate());
        });
        return out;
    }

    /** The last 12 months (the current one flagged partial), starting no earlier than the branch's first customer. */
    private Map<UUID, List<MonthStat>> loadMonths(UUID tenantId, List<UUID> ids, LocalDate today, Map<UUID, LocalDate> firstVisit) {
        YearMonth current = YearMonth.from(today);
        YearMonth first = current.minusMonths(11);
        Instant from = first.atDay(1).atStartOfDay(ZONE).toInstant();
        String sql = ARRIVALS_CTE + """
                SELECT a.branch_id,
                       to_char(a.arrived_at AT TIME ZONE 'Asia/Kolkata', 'YYYY-MM') AS ym,
                       COUNT(*) AS visits,
                       COALESCE(SUM(i.amount), 0) AS revenue,
                       COUNT(DISTINCT (a.arrived_at AT TIME ZONE 'Asia/Kolkata')::date) AS active_days
                FROM arrivals a
                LEFT JOIN inv i ON i.booking_id = a.id
                WHERE a.status <> 'CANCELLED' AND a.arrived_at >= :from
                GROUP BY 1, 2
                """;
        Map<UUID, Map<String, MonthStat>> raw = new HashMap<>();
        jdbc.query(sql, params(tenantId, ids).addValue("from", Timestamp.from(from)), rs -> {
            long visits = rs.getLong("visits");
            int active = rs.getInt("active_days");
            String ym = rs.getString("ym");
            raw.computeIfAbsent(rs.getObject("branch_id", UUID.class), k -> new HashMap<>()).put(ym,
                    new MonthStat(ym, visits, Math.round(rs.getDouble("revenue")), active,
                            active == 0 ? 0 : Math.round(visits * 10.0 / active) / 10.0, ym.equals(current.toString())));
        });
        Map<UUID, List<MonthStat>> out = new HashMap<>();
        for (UUID id : ids) {
            Map<String, MonthStat> byMonth = raw.getOrDefault(id, Map.of());
            YearMonth begin = first;
            LocalDate fv = firstVisit.get(id);
            if (fv != null && YearMonth.from(fv).isAfter(first)) begin = YearMonth.from(fv);
            List<MonthStat> list = new ArrayList<>();
            if (fv != null) {
                for (YearMonth m = begin; !m.isAfter(current); m = m.plusMonths(1)) {
                    list.add(byMonth.getOrDefault(m.toString(), new MonthStat(m.toString(), 0, 0, 0, 0, m.equals(current))));
                }
            }
            out.put(id, list);
        }
        return out;
    }

    /** Customers in the last 28 days vs the 28 before. */
    private Map<UUID, long[]> loadTrend(UUID tenantId, List<UUID> ids, LocalDate today) {
        Instant end = today.plusDays(1).atStartOfDay(ZONE).toInstant();
        Instant mid = today.minusDays(27).atStartOfDay(ZONE).toInstant();
        Instant begin = today.minusDays(55).atStartOfDay(ZONE).toInstant();
        String sql = ARRIVALS_CTE + """
                SELECT a.branch_id,
                       COUNT(*) FILTER (WHERE a.arrived_at >= :mid AND a.arrived_at < :end) AS last28,
                       COUNT(*) FILTER (WHERE a.arrived_at >= :begin AND a.arrived_at < :mid) AS prev28
                FROM arrivals a
                WHERE a.status <> 'CANCELLED' AND a.arrived_at >= :begin AND a.arrived_at < :end
                GROUP BY 1
                """;
        Map<UUID, long[]> out = new HashMap<>();
        jdbc.query(sql, params(tenantId, ids).addValue("mid", Timestamp.from(mid)).addValue("end", Timestamp.from(end))
                .addValue("begin", Timestamp.from(begin)), rs -> {
            out.put(rs.getObject("branch_id", UUID.class), new long[] {rs.getLong("last28"), rs.getLong("prev28")});
        });
        return out;
    }

    /** Staff-minutes on the floor for each weekday-hour, from attendance entry/exit times. */
    private void loadAttendance(UUID tenantId, List<Branch> branches, LocalDate start, LocalDate end,
                                Map<UUID, double[][]> minutesOut, Map<UUID, int[]> countsOut) {
        Map<UUID, int[]> hoursByBranch = branches.stream().collect(Collectors.toMap(Branch::getId, DemandInsightsService::openCloseHours));
        String sql = """
                SELECT branch_id, work_date, entry_time, exit_time
                FROM attendance_records
                WHERE tenant_id = :tenant AND branch_id IN (:branches)
                  AND work_date >= :start AND work_date <= :end AND entry_time IS NOT NULL
                """;
        Map<UUID, Set<LocalDate>> daysSeen = new HashMap<>();
        jdbc.query(sql, new MapSqlParameterSource("tenant", tenantId)
                .addValue("branches", new ArrayList<>(hoursByBranch.keySet()))
                .addValue("start", java.sql.Date.valueOf(start)).addValue("end", java.sql.Date.valueOf(end)), rs -> {
            UUID branch = rs.getObject("branch_id", UUID.class);
            Instant entry = rs.getTimestamp("entry_time").toInstant();
            Timestamp exitTs = rs.getTimestamp("exit_time");
            Instant exit = exitTs == null ? null : exitTs.toInstant();
            LocalDate day = rs.getDate("work_date").toLocalDate();
            double[][] grid = minutesOut.computeIfAbsent(branch, k -> new double[7][24]);
            addPresence(grid, entry, exit, hoursByBranch.get(branch)[1]);
            countsOut.computeIfAbsent(branch, k -> new int[2])[0]++;
            daysSeen.computeIfAbsent(branch, k -> new HashSet<>()).add(day);
        });
        daysSeen.forEach((b, days) -> countsOut.get(b)[1] = days.size());
    }

    /** Splits [entry, exit) across local hours. A missing exit is taken as closing time (or 8 h), capped at 14 h. */
    static void addPresence(double[][] grid, Instant entry, Instant exit, int closeHour) {
        Instant end = exit;
        if (end == null) {
            ZonedDateTime local = entry.atZone(ZONE);
            Instant closing = local.toLocalDate().atTime(Math.min(closeHour, 23), closeHour >= 24 ? 59 : 0).atZone(ZONE).toInstant();
            Instant eightHours = entry.plus(Duration.ofHours(8));
            end = closing.isAfter(entry) && closing.isBefore(eightHours) ? closing : eightHours;
        }
        Instant cap = entry.plus(Duration.ofHours(14));
        if (end.isAfter(cap)) end = cap;
        if (!end.isAfter(entry)) return;
        Instant cur = entry;
        while (cur.isBefore(end)) {
            ZonedDateTime z = cur.atZone(ZONE);
            Instant hourEnd = z.truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusHours(1).toInstant();
            Instant seg = end.isBefore(hourEnd) ? end : hourEnd;
            grid[z.getDayOfWeek().getValue() - 1][z.getHour()] += Duration.between(cur, seg).toSeconds() / 60.0;
            cur = seg;
        }
    }

    /**
     * Typical visit length per branch → [minutes, 1 if recorded else 0]. Prefers the median recorded service time of
     * completed visits (ignoring 1-minute test taps and day-long outliers), then the catalogue's estimated duration.
     */
    private Map<UUID, double[]> loadVisitMinutes(UUID tenantId, List<UUID> ids, Instant from, Instant to) {
        Map<UUID, double[]> out = new HashMap<>();
        Map<UUID, double[]> estimated = new HashMap<>();
        MapSqlParameterSource p = params(tenantId, ids).addValue("from", Timestamp.from(from)).addValue("to", Timestamp.from(to));
        jdbc.query("""
                SELECT b.branch_id, SUM(li.estimated_duration_minutes) AS minutes
                FROM bookings b JOIN booking_line_items li ON li.booking_id = b.id
                WHERE b.tenant_id = :tenant AND b.branch_id IN (:branches) AND b.deleted_at IS NULL
                  AND b.status = 'COMPLETED' AND b.created_at >= :from AND b.created_at < :to
                  AND li.estimated_duration_minutes IS NOT NULL
                GROUP BY b.id, b.branch_id
                """, p, rs -> {
            double m = rs.getDouble("minutes");
            if (m < 10 || m > 300) return;
            double[] acc = estimated.computeIfAbsent(rs.getObject("branch_id", UUID.class), k -> new double[2]);
            acc[0] += m;
            acc[1]++;
        });
        estimated.forEach((b, acc) -> {
            if (acc[1] >= MIN_RECORDED_DURATIONS) out.put(b, new double[] {acc[0] / acc[1], 0});
        });
        jdbc.query("""
                SELECT branch_id, percentile_cont(0.5) WITHIN GROUP (ORDER BY actual_duration_minutes) AS med, COUNT(*) AS n
                FROM bookings
                WHERE tenant_id = :tenant AND branch_id IN (:branches) AND deleted_at IS NULL AND status = 'COMPLETED'
                  AND created_at >= :from AND created_at < :to
                  AND actual_duration_minutes BETWEEN 10 AND 240
                GROUP BY branch_id
                """, p, rs -> {
            if (rs.getLong("n") >= MIN_RECORDED_DURATIONS) {
                out.put(rs.getObject("branch_id", UUID.class), new double[] {rs.getDouble("med"), 1});
            }
        });
        return out;
    }

    /** Average monthly salary of a branch's active staff spread over ~26 days × 9 hours, as ₹ per staff-hour. */
    private Map<UUID, Double> loadStaffHourCost(UUID tenantId, List<UUID> ids) {
        Map<UUID, Double> out = new HashMap<>();
        jdbc.query("SELECT branch_id, AVG(salary) AS avg_salary FROM staff WHERE tenant_id = :tenant AND branch_id IN (:branches) "
                        + "AND active = true AND salary IS NOT NULL AND salary > 0 GROUP BY branch_id",
                params(tenantId, ids), rs -> {
                    out.put(rs.getObject("branch_id", UUID.class), rs.getDouble("avg_salary") / (26.0 * 9.0));
                });
        return out;
    }

    private static MapSqlParameterSource params(UUID tenantId, List<UUID> ids) {
        return new MapSqlParameterSource("tenant", tenantId).addValue("branches", ids);
    }
}
