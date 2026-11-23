package com.salonplatform.forecast;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.enums.BranchStatus;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Expected sales for a brand and each of its branches, learned from the brand's own invoices.
 *
 * <p>Sales are the invoiced totals (voided invoices excluded) per branch per day in India time. The model is trained on
 * everything the brand has recorded up to yesterday, so it improves as history grows; nothing leaves the database. Results
 * are cached for a while because training re-runs the backtests, which is cheap but not free.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SalesForecastService {

    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    public static final int DEFAULT_DAYS = 30;
    public static final int MAX_DAYS = 90;
    private static final int HISTORY_DAYS = 120;
    private static final long CACHE_MINUTES = 120;

    private final NamedParameterJdbcTemplate jdbc;
    private final BranchRepository branches;

    @Value("${app.forecast.festival-prior-weight:0.5}")
    private double festivalPriorWeight = 0.5;

    private final Map<UUID, Trained> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Object> locks = new ConcurrentHashMap<>();

    // ---- response shapes

    public record HistoryPoint(LocalDate date, double revenue) {}

    /** Where this month is heading against the branch's monthly target. */
    public record Pacing(LocalDate monthStart, LocalDate monthEnd, double monthToDate, double expectedMonthEnd, double low, double high,
                         Double target, Double expectedVsTarget) {}

    public record Scope(UUID id, String name, boolean forecastable, String note, ForecastEngine.Quality quality, ForecastEngine.Totals totals,
                        List<ForecastEngine.Day> days, List<HistoryPoint> history, Pacing pacing) {}

    public record ForecastResponse(Instant generatedAt, LocalDate today, LocalDate trainedThrough, boolean stale, int horizonDays,
                                   LocalDate calendarEnds, double festivalAssumptionWeight, Scope brand, List<Scope> branches,
                                   List<ForecastEngine.FestivalImpact> festivals) {}

    /** What was learned for a tenant, kept for a while. */
    public record Trained(ForecastEngine.Output output, Map<UUID, Series> series, Map<UUID, Branch> branches, Instant at, LocalDate today) {}

    // ---- public API

    public ForecastResponse forecast(int days) {
        UserPrincipal user = SecurityUtils.currentUser();
        UUID tenantId = SecurityUtils.requireTenantId();
        SecurityUtils.assertManagerOrBrandAdmin();
        if (days < 7 || days > MAX_DAYS) throw new BadRequestException("days must be between 7 and " + MAX_DAYS);
        Trained t = trained(tenantId);
        ForecastResponse full = respond(t, days);
        if (SecurityUtils.isManagerRole()) {
            UUID own = user.getBranchId();
            if (own == null) throw new com.salonplatform.exception.ForbiddenException("Branch context required");
            SecurityUtils.assertBranchAccess(own);
            List<Scope> mine = full.branches().stream().filter(b -> b.id().equals(own)).toList();
            Scope only = mine.isEmpty() ? null : mine.get(0);
            return new ForecastResponse(full.generatedAt(), full.today(), full.trainedThrough(), full.stale(), full.horizonDays(), full.calendarEnds(),
                    full.festivalAssumptionWeight(), only, mine, full.festivals());
        }
        return full;
    }

    /** Trains (or reuses) the model for a tenant; used by the nightly job and by the assistant's tools. */
    public Trained trained(UUID tenantId) {
        LocalDate today = LocalDate.now(ZONE);
        Trained c = cache.get(tenantId);
        if (c != null && c.today().equals(today) && c.at().isAfter(Instant.now().minus(CACHE_MINUTES, ChronoUnit.MINUTES))) return c;
        synchronized (locks.computeIfAbsent(tenantId, k -> new Object())) {
            c = cache.get(tenantId);
            if (c != null && c.today().equals(today) && c.at().isAfter(Instant.now().minus(CACHE_MINUTES, ChronoUnit.MINUTES))) return c;
            Trained fresh = train(tenantId, today);
            if (fresh != null) cache.put(tenantId, fresh);
            else cache.remove(tenantId);
            if (fresh == null) {
                throw new BadRequestException("There are no sales to forecast from yet.");
            }
            return fresh;
        }
    }

    /** For the scheduled job: trains and logs how well the model did, without throwing for tenants without data. */
    public void warm(UUID tenantId) {
        try {
            cache.remove(tenantId);
            Trained t = trained(tenantId);
            ForecastEngine.Result b = t.output().brand();
            log.info("Sales forecast trained: tenant={} branches={} through={} brandModel={} confidence={} backtests={} monthError={} weekError={} dailyError={} bias={}",
                    tenantId, t.output().branches().size(), t.output().trainedThrough(), b.quality().model(), b.quality().confidence(),
                    b.quality().backtests(), pct(b.quality().monthError()), pct(b.quality().weekError()), pct(b.quality().dailyError()), pct(b.quality().bias()));
            for (ForecastEngine.Result r : t.output().branches()) {
                log.info("  branch {} forecastable={} model={} confidence={} trainingDays={} monthError={}", r.name(), r.forecastable(),
                        r.quality().model(), r.quality().confidence(), r.quality().trainingDays(), pct(r.quality().monthError()));
            }
        } catch (BadRequestException e) {
            log.debug("Sales forecast skipped for tenant {}: {}", tenantId, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Sales forecast training failed for tenant {}", tenantId, e);
        }
    }

    private static String pct(double v) {
        return v < 0 ? "n/a" : String.format("%.1f%%", v * 100);
    }

    // ---- training

    private Trained train(UUID tenantId, LocalDate today) {
        List<Branch> active = branches.findByTenantIdAndStatus(tenantId, BranchStatus.ACTIVE);
        if (active.isEmpty()) return null;
        Map<UUID, Branch> byId = new HashMap<>();
        for (Branch b : active) byId.put(b.getId(), b);

        Map<UUID, TreeMap<LocalDate, Double>> daily = new HashMap<>();
        jdbc.query("""
                SELECT branch_id, (issued_at AT TIME ZONE 'Asia/Kolkata')::date AS d, SUM(grand_total) AS revenue
                FROM invoices
                WHERE tenant_id = :tenant AND deleted_at IS NULL
                GROUP BY 1, 2
                """, new MapSqlParameterSource("tenant", tenantId), rs -> {
            UUID branchId = rs.getObject("branch_id", UUID.class);
            if (!byId.containsKey(branchId)) return;
            Date d = rs.getDate("d");
            daily.computeIfAbsent(branchId, k -> new TreeMap<>()).put(d.toLocalDate(), rs.getBigDecimal("revenue").doubleValue());
        });
        LocalDate yesterday = today.minusDays(1);
        LocalDate lastData = null;
        for (TreeMap<LocalDate, Double> m : daily.values()) {
            LocalDate last = m.headMap(today, false).isEmpty() ? null : m.headMap(today, false).lastKey();
            if (last != null && (lastData == null || last.isAfter(lastData))) lastData = last;
        }
        if (lastData == null) return null;
        LocalDate end = lastData.isAfter(yesterday) ? yesterday : lastData;

        List<Series> series = new ArrayList<>();
        Map<UUID, Series> seriesById = new HashMap<>();
        for (Branch b : active) {
            TreeMap<LocalDate, Double> m = daily.get(b.getId());
            if (m == null || m.isEmpty()) continue;
            LocalDate start = m.firstKey();
            if (start.isAfter(end)) continue;
            double[] rev = new double[(int) ChronoUnit.DAYS.between(start, end) + 1];
            for (Map.Entry<LocalDate, Double> e : m.entrySet()) {
                if (e.getKey().isAfter(end)) break;
                rev[(int) ChronoUnit.DAYS.between(start, e.getKey())] = e.getValue();
            }
            Series s = new Series(b.getId(), b.getName(), start, rev);
            series.add(s);
            seriesById.put(b.getId(), s);
        }
        if (series.isEmpty()) return null;

        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        LocalDate through = today.plusDays(MAX_DAYS - 1L).isAfter(monthEnd) ? today.plusDays(MAX_DAYS - 1L) : monthEnd;
        ForecastEngine engine = new ForecastEngine(FestivalCalendar.standard(), festivalPriorWeight);
        ForecastEngine.Output out = engine.forecast(series, through);
        return new Trained(out, seriesById, byId, Instant.now(), today);
    }

    // ---- shaping the answer

    private ForecastResponse respond(Trained t, int days) {
        LocalDate today = t.today();
        LocalDate trainedThrough = t.output().trainedThrough();
        boolean stale = trainedThrough.isBefore(today.minusDays(3));
        Scope brand = scope(t, t.output().brand(), null, days);
        List<Scope> scopes = new ArrayList<>();
        for (ForecastEngine.Result r : t.output().branches()) scopes.add(scope(t, r, t.series().get(r.id()), days));
        LocalDate last = today.plusDays(days - 1L);
        List<ForecastEngine.FestivalImpact> festivals = t.output().festivals().stream()
                .filter(f -> !f.date().isBefore(today.minusDays(1)) && !f.date().isAfter(last.plusDays(7))).toList();
        return new ForecastResponse(t.at(), today, trainedThrough, stale, days, t.output().calendarEnds(), festivalPriorWeight, brand, scopes, festivals);
    }

    private Scope scope(Trained t, ForecastEngine.Result r, Series series, int days) {
        LocalDate today = t.today();
        LocalDate last = today.plusDays(days - 1L);
        List<ForecastEngine.Day> window = r.days().stream().filter(d -> !d.date().isBefore(today) && !d.date().isAfter(last)).toList();
        ForecastEngine.Totals totals = r.totals() == null ? null : retotal(r, today);
        List<HistoryPoint> history = history(t, series);
        Pacing pacing = r.forecastable() ? pacing(t, r) : null;
        return new Scope(r.id(), r.name(), r.forecastable(), r.note(), r.quality(), totals, window, history, pacing);
    }

    /** Totals counted from today, keeping the same range proportions the backtest found. */
    private ForecastEngine.Totals retotal(ForecastEngine.Result r, LocalDate today) {
        double s7 = 0, s30 = 0;
        int i = 0;
        for (ForecastEngine.Day d : r.days()) {
            if (d.date().isBefore(today)) continue;
            if (i < 7) s7 += d.expected();
            if (i < 30) s30 += d.expected();
            i++;
        }
        ForecastEngine.Totals o = r.totals();
        double w7Lo = o.next7() > 0 ? o.next7Low() / o.next7() : 0.9, w7Hi = o.next7() > 0 ? o.next7High() / o.next7() : 1.1;
        double m30Lo = o.next30() > 0 ? o.next30Low() / o.next30() : 0.9, m30Hi = o.next30() > 0 ? o.next30High() / o.next30() : 1.1;
        return new ForecastEngine.Totals(s7, s7 * w7Lo, s7 * w7Hi, s30, s30 * m30Lo, s30 * m30Hi);
    }

    private List<HistoryPoint> history(Trained t, Series series) {
        LocalDate end = t.output().trainedThrough();
        LocalDate from = end.minusDays(HISTORY_DAYS - 1L);
        List<HistoryPoint> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(end); d = d.plusDays(1)) {
            double v = 0;
            if (series != null) v = series.at(d);
            else for (Series s : t.series().values()) v += s.at(d);
            if (series != null && !series.covers(d)) continue;
            out.add(new HistoryPoint(d, v));
        }
        return out;
    }

    private Pacing pacing(Trained t, ForecastEngine.Result r) {
        LocalDate today = t.today();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        LocalDate trainedThrough = t.output().trainedThrough();
        double mtd = 0;
        boolean brandScope = r.id().getMostSignificantBits() == 0 && r.id().getLeastSignificantBits() == 0;
        Iterable<Series> relevant = brandScope ? t.series().values() : List.of(t.series().get(r.id()));
        for (Series s : relevant) {
            for (LocalDate d = monthStart; !d.isAfter(trainedThrough) && !d.isAfter(monthEnd); d = d.plusDays(1)) mtd += s.at(d);
        }
        double remaining = 0;
        int remainingDays = 0;
        for (ForecastEngine.Day d : r.days()) {
            if (!d.date().isBefore(monthStart) && !d.date().isAfter(monthEnd) && d.date().isAfter(trainedThrough)) {
                remaining += d.expected();
                remainingDays++;
            }
        }
        ForecastEngine.Totals o = r.totals();
        boolean short7 = remainingDays <= 10;
        double lo = o == null ? 0.9 : (short7 ? ratio(o.next7Low(), o.next7(), 0.9) : ratio(o.next30Low(), o.next30(), 0.9));
        double hi = o == null ? 1.1 : (short7 ? ratio(o.next7High(), o.next7(), 1.1) : ratio(o.next30High(), o.next30(), 1.1));
        Double target = null;
        if (brandScope) {
            double sum = 0;
            boolean all = !t.series().isEmpty();
            for (UUID id : t.series().keySet()) {
                Branch b = t.branches().get(id);
                if (b == null || b.getMonthlySalesTarget() == null || b.getMonthlySalesTarget().signum() <= 0) all = false;
                else sum += b.getMonthlySalesTarget().doubleValue();
            }
            if (all) target = sum;
        } else {
            Branch b = t.branches().get(r.id());
            if (b != null && b.getMonthlySalesTarget() != null && b.getMonthlySalesTarget().signum() > 0) target = b.getMonthlySalesTarget().doubleValue();
        }
        double expected = mtd + remaining;
        return new Pacing(monthStart, monthEnd, mtd, expected, mtd + remaining * lo, mtd + remaining * hi, target,
                target == null ? null : expected / target - 1);
    }

    private static double ratio(double a, double b, double fallback) {
        return b > 0 ? a / b : fallback;
    }
}
