package com.salonplatform.forecast;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.enums.BranchStatus;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ForbiddenException;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Will each branch have enough of every product for the services it is about to do?
 *
 * <p>The app does not record which product a service uses, so it is learned: for each product, how much gets used per service
 * in each category, from past stock movements and the services done each week ({@link UsageModel}). That is applied to the
 * services already booked, plus the walk-in business the sales forecast expects on top, and compared with what is on the shelf.
 */
@Service
@RequiredArgsConstructor
public class StockReadinessService {

    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    public static final int DEFAULT_DAYS = 7;
    public static final int MAX_DAYS = 30;
    private static final int HISTORY_WEEKS = 52;
    private static final int MIX_WEEKS = 8;
    private static final int MAX_CATEGORIES = 8;
    private static final double SHORT_ROUND = 1.0;

    private final NamedParameterJdbcTemplate jdbc;
    private final BranchRepository branches;
    private final SalesForecastService sales;

    public enum Status { SHORT, LOW, OK, UNKNOWN }

    /** One branch-week of the regression: services done by category. */
    private record Row(LocalDate week, UUID branch, double[] lines) {}

    public record Item(UUID productId, String name, String unit, String category, double onHand, double reorderLevel, double bookedNeed,
                       double expectedNeed, LocalDate runsOutOn, Double daysOfCover, double suggestedOrder, Status status, String basis,
                       ForecastEngine.Confidence confidence) {}

    public record BranchReadiness(UUID branchId, String name, int shortCount, int lowCount, int okCount, int unknownCount, int bookedServices,
                                  List<Item> items, String note) {}

    public record Readiness(LocalDate from, LocalDate to, int days, boolean hasUsageHistory, List<BranchReadiness> branches) {}

    public Readiness readiness(int days) {
        UserPrincipal user = SecurityUtils.currentUser();
        UUID tenantId = SecurityUtils.requireTenantId();
        SecurityUtils.assertManagerOrBrandAdmin();
        if (days < 1 || days > MAX_DAYS) throw new BadRequestException("days must be between 1 and " + MAX_DAYS);
        UUID only = null;
        if (SecurityUtils.isManagerRole()) {
            only = user.getBranchId();
            if (only == null) throw new ForbiddenException("Branch context required");
            SecurityUtils.assertBranchAccess(only);
        }
        return compute(tenantId, days, only, LocalDate.now(ZONE));
    }

    // ---- the work

    Readiness compute(UUID tenantId, int days, UUID onlyBranch, LocalDate today) {
        List<Branch> active = branches.findByTenantIdAndStatus(tenantId, BranchStatus.ACTIVE).stream()
                .filter(b -> onlyBranch == null || b.getId().equals(onlyBranch)).toList();
        LocalDate thisWeek = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate from = thisWeek.minusWeeks(HISTORY_WEEKS);

        // services done per branch, category and week
        Map<UUID, Map<LocalDate, Map<UUID, Double>>> done = new HashMap<>();
        Map<UUID, Double> categoryVolume = new HashMap<>();
        jdbc.query("""
                SELECT b.branch_id, s.category_id,
                       date_trunc('week', (COALESCE(b.completed_at, b.scheduled_start_at, b.created_at) AT TIME ZONE 'Asia/Kolkata')::date)::date AS week,
                       SUM(COALESCE(li.quantity, 1)) AS lines
                FROM booking_line_items li
                JOIN bookings b ON b.id = li.booking_id
                JOIN services s ON s.id = li.service_id
                WHERE b.tenant_id = :t AND b.status = 'COMPLETED' AND b.deleted_at IS NULL
                  AND COALESCE(b.completed_at, b.scheduled_start_at, b.created_at) >= :from
                  AND COALESCE(b.completed_at, b.scheduled_start_at, b.created_at) < :to
                GROUP BY 1, 2, 3
                """, new MapSqlParameterSource("t", tenantId)
                .addValue("from", Timestamp.from(from.atStartOfDay(ZONE).toInstant()))
                .addValue("to", Timestamp.from(thisWeek.atStartOfDay(ZONE).toInstant())), rs -> {
            UUID branch = rs.getObject("branch_id", UUID.class);
            UUID cat = rs.getObject("category_id", UUID.class);
            LocalDate week = rs.getDate("week").toLocalDate();
            double lines = rs.getDouble("lines");
            done.computeIfAbsent(branch, k -> new TreeMap<>()).computeIfAbsent(week, k -> new HashMap<>()).merge(cat, lines, Double::sum);
            categoryVolume.merge(cat, lines, Double::sum);
        });

        List<UUID> categories = categoryVolume.entrySet().stream().sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(MAX_CATEGORIES).map(Map.Entry::getKey).toList();
        Map<UUID, Integer> catIndex = new HashMap<>();
        for (int i = 0; i < categories.size(); i++) catIndex.put(categories.get(i), i);
        int k = categories.size() + 1; // the last slot gathers every other category

        // product usage per branch, product and week
        Map<UUID, Map<UUID, Map<LocalDate, Double>>> usage = new HashMap<>(); // product -> branch -> week -> quantity
        jdbc.query("""
                SELECT branch_id, product_id, date_trunc('week', movement_date)::date AS week, SUM(quantity) AS qty
                FROM inventory_movements
                WHERE tenant_id = :t AND movement_type IN ('USAGE', 'WASTAGE', 'RETAIL_SALE')
                  AND movement_date >= :from AND movement_date < :to
                GROUP BY 1, 2, 3
                """, new MapSqlParameterSource("t", tenantId).addValue("from", java.sql.Date.valueOf(from)).addValue("to", java.sql.Date.valueOf(thisWeek)), rs -> {
            usage.computeIfAbsent(rs.getObject("product_id", UUID.class), p -> new HashMap<>())
                    .computeIfAbsent(rs.getObject("branch_id", UUID.class), b -> new HashMap<>())
                    .merge(rs.getDate("week").toLocalDate(), rs.getDouble("qty"), Double::sum);
        });

        // rows for the regression: one per branch-week with services, ordered by week
        List<Row> rows = new ArrayList<>();
        Map<UUID, double[]> mix = new HashMap<>();
        double[] tenantMix = new double[k];
        for (var be : done.entrySet()) {
            double[] branchMix = new double[k];
            for (var we : be.getValue().entrySet()) {
                double[] lines = new double[k];
                for (var ce : we.getValue().entrySet()) {
                    Integer idx = catIndex.get(ce.getKey());
                    lines[idx == null ? k - 1 : idx] += ce.getValue();
                }
                if (Arrays.stream(lines).sum() > 0) rows.add(new Row(we.getKey(), be.getKey(), lines));
                if (!we.getKey().isBefore(thisWeek.minusWeeks(MIX_WEEKS))) {
                    for (int i = 0; i < k; i++) {
                        branchMix[i] += lines[i];
                        tenantMix[i] += lines[i];
                    }
                }
            }
            mix.put(be.getKey(), branchMix);
        }
        rows.sort(Comparator.comparing(Row::week).thenComparing(r -> r.branch().toString()));
        double[][] x = rows.stream().map(Row::lines).toArray(double[][]::new);

        // stock on hand
        record Stock(UUID branch, UUID product, String name, String unit, String category, double onHand, double reorder) {}
        List<Stock> stock = new ArrayList<>();
        jdbc.query("""
                SELECT bi.branch_id, bi.product_id, bi.quantity, p.name, p.unit, p.category, COALESCE(p.reorder_level, 0) AS reorder_level
                FROM branch_inventory bi JOIN inventory_products p ON p.id = bi.product_id
                WHERE bi.tenant_id = :t AND p.active = TRUE
                """, new MapSqlParameterSource("t", tenantId), rs -> {
            stock.add(new Stock(rs.getObject("branch_id", UUID.class), rs.getObject("product_id", UUID.class), rs.getString("name"),
                    rs.getString("unit"), rs.getString("category"), rs.getDouble("quantity"), rs.getDouble("reorder_level")));
        });

        // services already booked, per branch, day and category
        Map<UUID, Map<LocalDate, double[]>> booked = new HashMap<>();
        LocalDate last = today.plusDays(days - 1L);
        jdbc.query("""
                SELECT b.branch_id, s.category_id, (b.scheduled_start_at AT TIME ZONE 'Asia/Kolkata')::date AS d, SUM(COALESCE(li.quantity, 1)) AS lines
                FROM booking_line_items li
                JOIN bookings b ON b.id = li.booking_id
                JOIN services s ON s.id = li.service_id
                WHERE b.tenant_id = :t AND b.deleted_at IS NULL AND b.status IN ('CONFIRMED', 'IN_PROGRESS')
                  AND b.scheduled_start_at >= :from AND b.scheduled_start_at < :to
                GROUP BY 1, 2, 3
                """, new MapSqlParameterSource("t", tenantId)
                .addValue("from", Timestamp.from(today.atStartOfDay(ZONE).toInstant()))
                .addValue("to", Timestamp.from(last.plusDays(1).atStartOfDay(ZONE).toInstant())), rs -> {
            Integer idx = catIndex.get(rs.getObject("category_id", UUID.class));
            booked.computeIfAbsent(rs.getObject("branch_id", UUID.class), b -> new HashMap<>())
                    .computeIfAbsent(rs.getDate("d").toLocalDate(), d -> new double[k])[idx == null ? k - 1 : idx] += rs.getDouble("lines");
        });

        // expected services per day per branch, from the sales forecast when there is one
        SalesForecastService.Trained trained = null;
        try {
            trained = sales.trained(tenantId);
        } catch (BadRequestException ignored) {
            // no sales history: fall back to the recent pace
        }

        Map<String, UsageModel> models = new HashMap<>();
        boolean anyUsage = false;
        List<BranchReadiness> out = new ArrayList<>();
        for (Branch b : active) {
            double[] branchMix = mix.getOrDefault(b.getId(), new double[k]);
            double mixTotal = Arrays.stream(branchMix).sum();
            if (mixTotal <= 0) {
                branchMix = tenantMix;
                mixTotal = Arrays.stream(tenantMix).sum();
            }
            double recentLinesPerDay = mixTotal / (MIX_WEEKS * 7.0);
            double revPerLine = 0;
            Series series = trained == null ? null : trained.series().get(b.getId());
            if (series != null) {
                double rev = 0;
                for (int i = 0; i < MIX_WEEKS * 7; i++) rev += series.at(thisWeek.minusDays(1L + i));
                double lines = done.getOrDefault(b.getId(), Map.of()).entrySet().stream()
                        .filter(e -> !e.getKey().isBefore(thisWeek.minusWeeks(MIX_WEEKS))).mapToDouble(e -> e.getValue().values().stream().mapToDouble(Double::doubleValue).sum()).sum();
                if (lines > 0) revPerLine = rev / lines;
            }
            Map<LocalDate, Double> forecastRevenue = new HashMap<>();
            if (trained != null) {
                for (ForecastEngine.Result r : trained.output().branches()) {
                    if (r.id().equals(b.getId())) for (ForecastEngine.Day d : r.days()) forecastRevenue.put(d.date(), d.expected());
                }
            }

            // per-day demand by category over the horizon
            double[][] demand = new double[days][k];
            double[] bookedTotal = new double[k];
            double bookedServices = 0;
            for (int d = 0; d < days; d++) {
                LocalDate date = today.plusDays(d);
                double[] bk = booked.getOrDefault(b.getId(), Map.of()).getOrDefault(date, new double[k]);
                double bkSum = Arrays.stream(bk).sum();
                bookedServices += bkSum;
                double expectedTotal = revPerLine > 0 && forecastRevenue.containsKey(date) ? forecastRevenue.get(date) / revPerLine : recentLinesPerDay;
                double extra = Math.max(0, expectedTotal - bkSum);
                for (int i = 0; i < k; i++) {
                    demand[d][i] = bk[i] + (mixTotal > 0 ? extra * branchMix[i] / mixTotal : 0);
                    bookedTotal[i] += bk[i];
                }
            }

            List<Item> items = new ArrayList<>();
            int shortN = 0, lowN = 0, okN = 0, unknownN = 0;
            for (Stock s : stock) {
                if (!s.branch().equals(b.getId())) continue;
                UsageModel m = models.computeIfAbsent(s.product().toString(), key -> fitProduct(usage.get(s.product()), rows, x));
                if (m.mode() != UsageModel.Mode.NONE) anyUsage = true;
                Item item = assess(s.product(), s.name(), s.unit(), s.category(), s.onHand(), s.reorder(), m, demand, bookedTotal, today, days);
                items.add(item);
                switch (item.status()) {
                    case SHORT -> shortN++;
                    case LOW -> lowN++;
                    case OK -> okN++;
                    case UNKNOWN -> unknownN++;
                }
            }
            items.removeIf(i -> i.status() == Status.OK);
            items.sort(Comparator.comparing((Item i) -> i.status().ordinal())
                    .thenComparing(i -> i.runsOutOn() == null ? LocalDate.MAX : i.runsOutOn()).thenComparing(Item::name));
            String note = null;
            if (series == null && revPerLine <= 0) note = "Expected walk-in demand uses the recent pace because this branch has no sales history yet.";
            out.add(new BranchReadiness(b.getId(), b.getName(), shortN, lowN, okN, unknownN, (int) Math.round(bookedServices), items, note));
        }
        return new Readiness(today, last, days, anyUsage, out);
    }

    private static UsageModel fitProduct(Map<UUID, Map<LocalDate, Double>> perBranch, List<Row> rows, double[][] x) {
        if (perBranch == null || x.length < UsageModel.MIN_WEEKS) return UsageModel.none(0);
        double[] y = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            Map<LocalDate, Double> weeks = perBranch.get(rows.get(i).branch());
            y[i] = weeks == null ? 0 : weeks.getOrDefault(rows.get(i).week(), 0.0);
        }
        return UsageModel.fit(x, y);
    }

    private Item assess(UUID productId, String name, String unit, String category, double onHand, double reorder, UsageModel m, double[][] demand,
                        double[] bookedTotal, LocalDate today, int days) {
        if (m.mode() == UsageModel.Mode.NONE) {
            Status st = reorder > 0 && onHand < reorder ? Status.LOW : Status.UNKNOWN;
            return new Item(productId, name, unit, category, onHand, reorder, 0, 0, null, null, st == Status.LOW ? roundUp(unit, reorder * 2 - onHand) : 0,
                    st, "NONE", ForecastEngine.Confidence.INSUFFICIENT);
        }
        double cumulative = 0;
        LocalDate runsOut = null;
        double total = 0;
        for (int d = 0; d < days; d++) {
            double need = m.need(demand[d], 1);
            cumulative += need;
            total += need;
            if (runsOut == null && cumulative > onHand) runsOut = today.plusDays(d);
        }
        double bookedNeed = m.need(bookedTotal, 0);
        double perDay = total / days;
        Double cover = perDay > 0 ? onHand / perDay : null;
        Status st = onHand < total ? Status.SHORT : onHand - total < reorder ? Status.LOW : Status.OK;
        double suggested = st == Status.OK ? 0 : roundUp(unit, Math.max(0, total + reorder - onHand));
        return new Item(productId, name, unit, category, onHand, reorder, bookedNeed, total, runsOut, cover, suggested, st,
                m.mode().name(), m.confidence());
    }

    private static double roundUp(String unit, double v) {
        if (v <= 0) return 0;
        return switch (unit) {
            case "ML", "G" -> Math.ceil(v / 50.0) * 50.0;
            default -> Math.ceil(v / SHORT_ROUND);
        };
    }

    /** Days between, for tests and callers. */
    static long daysBetween(LocalDate a, LocalDate b) {
        return ChronoUnit.DAYS.between(a, b);
    }
}
