package com.salonplatform.service;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.Tenant;
import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.TenantRepository;
import com.salonplatform.domain.repository.UserRepository;
import com.salonplatform.dto.platform.PlatformUsageInsights;
import com.salonplatform.dto.platform.PlatformUsageInsights.*;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.telemetry.ProductTelemetryReadiness;
import com.salonplatform.telemetry.ProductUsageSqlSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Adoption analytics layered on {@code product_events}: change vs the previous period, an explainable health score,
 * per-feature last-used times and trends, and feature × branch / user matrices. Kept separate from
 * {@link PlatformUsageAnalyticsService} so the existing screens are untouched.
 *
 * <p>SQL is assembled with explicit strings on purpose (see PRODUCT_CONTEXT.md §4.3.1, pitfall 1).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformUsageInsightsService {

    /** Buckets per sparkline; longer ranges are aggregated so payloads stay small. */
    private static final int MAX_TREND_POINTS = 60;
    private static final int TOP_FEATURES_PORTFOLIO = 40;
    private static final int TOP_FEATURES_MATRIX = 12;
    private static final int TOP_USERS_TREND = 25;
    private static final int RECENT_ACTIVITY = 60;
    private static final int DORMANT_AFTER_DAYS = 14;
    private static final int CORE_FEATURE_SET = 8;

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ProductTelemetryReadiness telemetryReadiness;
    private final ProductUsageSqlSupport usageSql;

    // ------------------------------------------------------------------ portfolio

    public PlatformUsageInsights.Portfolio portfolio(Instant from, Instant to, boolean includeDemo) {
        SecurityUtils.assertPlatformAdmin();
        usageSql.ensureTelemetrySchema();
        Range cur = Range.of(from, to);
        Range prev = cur.previous();

        List<Tenant> tenants = tenantRepository.findAll().stream()
                .filter(t -> includeDemo || !t.isDemo())
                .sorted(Comparator.comparing(Tenant::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (tenants.isEmpty()) {
            return emptyPortfolio(cur, prev, 0);
        }
        List<UUID> ids = tenants.stream().map(Tenant::getId).toList();
        Scope scope = Scope.tenants(ids);
        String dim = usageSql.featureDimensionSql();

        Map<UUID, Agg> curAgg = aggByTenant(cur, scope);
        Map<UUID, Agg> prevAgg = aggByTenant(prev, scope);
        Map<UUID, Integer> wau = distinctUsersByTenant(Range.ofDates(cur.toDate().minusDays(6), cur.toDate()), scope);
        Map<UUID, Integer> mau = distinctUsersByTenant(Range.ofDates(cur.toDate().minusDays(29), cur.toDate()), scope);
        Map<UUID, Map<LocalDate, Integer>> daily = dailyByTenant(cur, scope);
        Map<UUID, Integer> provisioned = provisionedByTenant(ids);

        List<FeatureReach> reach = featureReach(cur, prev, scope, dim);
        int platformFeatures = countDistinctFeatures(cur, scope, dim);

        List<AccountInsight> accounts = new ArrayList<>();
        for (Tenant t : tenants) {
            Agg a = curAgg.getOrDefault(t.getId(), Agg.EMPTY);
            Agg p = prevAgg.getOrDefault(t.getId(), Agg.EMPTY);
            Totals totals = Totals.builder()
                    .events(a.events())
                    .previousEvents(p.events())
                    .activeUsers(a.users())
                    .previousActiveUsers(p.users())
                    .wau(wau.getOrDefault(t.getId(), 0))
                    .mau(mau.getOrDefault(t.getId(), 0))
                    .provisionedUsers(provisioned.getOrDefault(t.getId(), 0))
                    .distinctFeatures(a.features())
                    .lastActivityAt(a.last())
                    .build();
            accounts.add(AccountInsight.builder()
                    .tenantId(t.getId())
                    .tenantName(t.getName())
                    .tenantSlug(t.getSlug())
                    .demoTenant(t.isDemo())
                    .totals(totals)
                    .health(health(totals, platformFeatures, cur))
                    .trend(bucketize(daily.get(t.getId()), cur))
                    .build());
        }

        Totals platform = platformTotals(cur, prev, scope, accounts);
        return PlatformUsageInsights.Portfolio.builder()
                .from(cur.from())
                .to(cur.toExclusive().minusSeconds(1))
                .previousFrom(prev.fromDate())
                .previousTo(prev.toDate())
                .rangeDays(cur.days())
                .telemetryReady(telemetryReadiness.isEventsTableReady())
                .platform(platform)
                .segments(segments(accounts))
                .accounts(accounts)
                .featureReach(reach)
                .platformFeatureCount(platformFeatures)
                .totalBrands(tenants.size())
                .build();
    }

    private PlatformUsageInsights.Portfolio emptyPortfolio(Range cur, Range prev, int brands) {
        return PlatformUsageInsights.Portfolio.builder()
                .from(cur.from())
                .to(cur.toExclusive().minusSeconds(1))
                .previousFrom(prev.fromDate())
                .previousTo(prev.toDate())
                .rangeDays(cur.days())
                .telemetryReady(telemetryReadiness.isEventsTableReady())
                .platform(Totals.builder().build())
                .segments(List.of())
                .accounts(List.of())
                .featureReach(List.of())
                .totalBrands(brands)
                .build();
    }

    private Totals platformTotals(Range cur, Range prev, Scope scope, List<AccountInsight> accounts) {
        int events = accounts.stream().mapToInt(a -> a.getTotals().getEvents()).sum();
        int previousEvents = accounts.stream().mapToInt(a -> a.getTotals().getPreviousEvents()).sum();
        int provisioned = accounts.stream().mapToInt(a -> a.getTotals().getProvisionedUsers()).sum();
        Instant last = accounts.stream()
                .map(a -> a.getTotals().getLastActivityAt())
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return Totals.builder()
                .events(events)
                .previousEvents(previousEvents)
                .activeUsers(distinctUsers(cur, scope))
                .previousActiveUsers(distinctUsers(prev, scope))
                .wau(distinctUsers(Range.ofDates(cur.toDate().minusDays(6), cur.toDate()), scope))
                .mau(distinctUsers(Range.ofDates(cur.toDate().minusDays(29), cur.toDate()), scope))
                .provisionedUsers(provisioned)
                .distinctFeatures(countDistinctFeatures(cur, scope, usageSql.featureDimensionSql()))
                .lastActivityAt(last)
                .build();
    }

    private static List<SegmentCount> segments(List<AccountInsight> accounts) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String s : List.of("HEALTHY", "WATCH", "AT_RISK", "DORMANT", "NEW")) {
            counts.put(s, 0);
        }
        for (AccountInsight a : accounts) {
            counts.merge(a.getHealth().getStatus(), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .map(e -> SegmentCount.builder().status(e.getKey()).count(e.getValue()).build())
                .toList();
    }

    private List<FeatureReach> featureReach(Range cur, Range prev, Scope scope, String dim) {
        Where w = Where.of(cur, scope);
        String sql = "SELECT " + dim + " AS f, COUNT(*) AS hits, COUNT(DISTINCT user_id) AS users, "
                + "COUNT(DISTINCT tenant_id) AS brands, MAX(occurred_at) AS last_at "
                + "FROM product_events WHERE " + w.sql + " GROUP BY 1 ORDER BY hits DESC LIMIT " + TOP_FEATURES_PORTFOLIO;
        List<Object[]> rows = query(sql, (rs, i) -> new Object[] {
                rs.getString("f"), rs.getInt("hits"), rs.getInt("users"), rs.getInt("brands"), rs.getTimestamp("last_at")
        }, w.args);
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<String> top = rows.stream().map(r -> (String) r[0]).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Integer> prevHits = featureHits(prev, scope, dim);
        Map<String, Map<LocalDate, Integer>> daily = featureDaily(cur, scope, dim, top);
        List<FeatureReach> out = new ArrayList<>();
        for (Object[] r : rows) {
            String key = (String) r[0];
            out.add(FeatureReach.builder()
                    .featureKey(key)
                    .hits((Integer) r[1])
                    .previousHits(prevHits.getOrDefault(key, 0))
                    .uniqueUsers((Integer) r[2])
                    .brands((Integer) r[3])
                    .lastUsedAt(toInstant((Timestamp) r[4]))
                    .trend(bucketize(daily.get(key), cur))
                    .build());
        }
        return out;
    }

    // ------------------------------------------------------------------ one brand

    public PlatformUsageInsights.Tenant tenant(UUID tenantId, Instant from, Instant to, UUID branchId) {
        SecurityUtils.assertPlatformAdmin();
        usageSql.ensureTelemetrySchema();
        com.salonplatform.domain.entity.Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        Range cur = Range.of(from, to);
        Range prev = cur.previous();
        Scope scope = Scope.tenant(tenantId, branchId);
        String dim = usageSql.featureDimensionSql();

        List<Branch> branches = branchRepository.findByTenantId(tenantId);
        Map<UUID, Branch> branchById = branches.stream().collect(Collectors.toMap(Branch::getId, b -> b, (a, b) -> a));
        List<User> users = userRepository.findAll().stream()
                .filter(u -> tenantId.equals(u.getTenantId()))
                .filter(u -> branchId == null || branchId.equals(u.getBranchId()))
                .toList();
        int provisioned = (int) users.stream().filter(User::isActive).count();

        Agg curAgg = aggByTenant(cur, scope).getOrDefault(tenantId, Agg.EMPTY);
        Agg prevAgg = aggByTenant(prev, scope).getOrDefault(tenantId, Agg.EMPTY);
        Totals totals = Totals.builder()
                .events(curAgg.events())
                .previousEvents(prevAgg.events())
                .activeUsers(curAgg.users())
                .previousActiveUsers(prevAgg.users())
                .wau(distinctUsers(Range.ofDates(cur.toDate().minusDays(6), cur.toDate()), scope))
                .mau(distinctUsers(Range.ofDates(cur.toDate().minusDays(29), cur.toDate()), scope))
                .provisionedUsers(provisioned)
                .distinctFeatures(curAgg.features())
                .lastActivityAt(curAgg.last())
                .build();

        List<FeatureRow> features = featureRows(cur, prev, scope, dim, provisioned);
        Set<String> topKeys = features.stream()
                .limit(TOP_FEATURES_MATRIX)
                .map(FeatureRow::getFeatureKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<UserRow> userRows = userRows(cur, prev, scope, dim, users, branchById);
        Set<UUID> topUsers = userRows.stream()
                .filter(u -> u.getEvents() > 0)
                .limit(TOP_USERS_TREND)
                .map(UserRow::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Platform-wide feature count is the denominator for "breadth" so a brand is judged against what exists.
        int platformFeatures = countDistinctFeatures(cur, Scope.all(), dim);

        return PlatformUsageInsights.Tenant.builder()
                .tenantId(tenantId)
                .tenantName(tenant.getName())
                .from(cur.from())
                .to(cur.toExclusive().minusSeconds(1))
                .previousFrom(prev.fromDate())
                .previousTo(prev.toDate())
                .rangeDays(cur.days())
                .telemetryReady(telemetryReadiness.isEventsTableReady())
                .totals(totals)
                .health(health(totals, platformFeatures, cur))
                .daily(dailyPoints(cur, scope))
                .features(features)
                .branches(branchRows(cur, prev, scope, dim, branches, users))
                .users(attachUserTrends(userRows, userDaily(cur, scope, topUsers), cur))
                .featureBranchMatrix(topKeys.isEmpty() ? List.of() : matrix(cur, scope, dim, topKeys, "branch_id", null))
                .featureUserMatrix(topKeys.isEmpty() || topUsers.isEmpty()
                        ? List.of()
                        : matrix(cur, scope, dim, topKeys, "user_id", topUsers))
                .recentActivity(recentActivity(cur, scope, dim, users, branchById))
                .build();
    }

    private List<DailyPoint> dailyPoints(Range cur, Scope scope) {
        Where w = Where.of(cur, scope);
        String sql = "SELECT " + dayExpr() + " AS d, COALESCE(NULLIF(portal, ''), 'other') AS p, COUNT(*) AS c "
                + "FROM product_events WHERE " + w.sql + " GROUP BY 1, 2";
        Map<LocalDate, Map<String, Integer>> byDay = new HashMap<>();
        for (Object[] r : query(sql, (rs, i) -> new Object[] {
                rs.getDate("d").toLocalDate(), rs.getString("p"), rs.getInt("c")}, w.args)) {
            byDay.computeIfAbsent((LocalDate) r[0], k -> new HashMap<>()).merge((String) r[1], (Integer) r[2], Integer::sum);
        }
        String usersSql = "SELECT " + dayExpr() + " AS d, COUNT(DISTINCT user_id) AS u "
                + "FROM product_events WHERE " + w.sql + " GROUP BY 1";
        Map<LocalDate, Integer> activeUsers = new HashMap<>();
        for (Object[] r : query(usersSql, (rs, i) -> new Object[] {rs.getDate("d").toLocalDate(), rs.getInt("u")}, w.args)) {
            activeUsers.put((LocalDate) r[0], (Integer) r[1]);
        }
        List<DailyPoint> out = new ArrayList<>();
        for (LocalDate d = cur.fromDate(); !d.isAfter(cur.toDate()); d = d.plusDays(1)) {
            Map<String, Integer> p = byDay.getOrDefault(d, Map.of());
            int admin = p.getOrDefault("admin", 0);
            int manager = p.getOrDefault("manager", 0);
            int employee = p.getOrDefault("employee", 0);
            int total = p.values().stream().mapToInt(Integer::intValue).sum();
            out.add(DailyPoint.builder()
                    .date(d)
                    .events(total)
                    .activeUsers(activeUsers.getOrDefault(d, 0))
                    .admin(admin)
                    .manager(manager)
                    .employee(employee)
                    .other(Math.max(0, total - admin - manager - employee))
                    .build());
        }
        return out;
    }

    private List<FeatureRow> featureRows(Range cur, Range prev, Scope scope, String dim, int provisioned) {
        Where w = Where.of(cur, scope);
        String sql = "SELECT " + dim + " AS f, COUNT(*) AS hits, COUNT(DISTINCT user_id) AS users, "
                + "MIN(occurred_at) AS first_at, MAX(occurred_at) AS last_at "
                + "FROM product_events WHERE " + w.sql + " GROUP BY 1 ORDER BY hits DESC LIMIT 60";
        List<Object[]> rows = query(sql, (rs, i) -> new Object[] {
                rs.getString("f"), rs.getInt("hits"), rs.getInt("users"), rs.getTimestamp("first_at"), rs.getTimestamp("last_at")
        }, w.args);
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<String> keys = rows.stream().map(r -> (String) r[0]).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Integer> prevHits = featureHits(prev, scope, dim);
        Map<String, Map<LocalDate, Integer>> daily = featureDaily(cur, scope, dim, keys);
        List<FeatureRow> out = new ArrayList<>();
        for (Object[] r : rows) {
            String key = (String) r[0];
            int uniq = (Integer) r[2];
            out.add(FeatureRow.builder()
                    .featureKey(key)
                    .hits((Integer) r[1])
                    .previousHits(prevHits.getOrDefault(key, 0))
                    .uniqueUsers(uniq)
                    .userReachPct(provisioned > 0 ? Math.min(100, Math.round(uniq * 100f / provisioned)) : 0)
                    .firstUsedAt(toInstant((Timestamp) r[3]))
                    .lastUsedAt(toInstant((Timestamp) r[4]))
                    .trend(bucketize(daily.get(key), cur))
                    .build());
        }
        return out;
    }

    private List<BranchRow> branchRows(
            Range cur, Range prev, Scope scope, String dim, List<Branch> branches, List<User> users) {
        Map<UUID, BranchAgg> curAgg = branchAgg(cur, scope, dim);
        Map<UUID, BranchAgg> prevAgg = branchAgg(prev, scope, dim);
        Map<UUID, Map<LocalDate, Integer>> daily = branchDaily(cur, scope);
        Map<UUID, String> topFeature = topFeatureBy("branch_id", cur, scope, dim);
        Map<UUID, Long> provisioned = users.stream()
                .filter(User::isActive)
                .filter(u -> u.getBranchId() != null)
                .collect(Collectors.groupingBy(User::getBranchId, Collectors.counting()));

        List<BranchRow> out = new ArrayList<>();
        for (Branch b : branches) {
            if (scope.branchId() != null && !scope.branchId().equals(b.getId())) {
                continue;
            }
            BranchAgg a = curAgg.getOrDefault(b.getId(), BranchAgg.EMPTY);
            BranchAgg p = prevAgg.getOrDefault(b.getId(), BranchAgg.EMPTY);
            out.add(BranchRow.builder()
                    .branchId(b.getId())
                    .branchName(b.getName())
                    .branchCode(b.getCode())
                    .provisionedUsers(provisioned.getOrDefault(b.getId(), 0L).intValue())
                    .activeUsers(a.users())
                    .events(a.events())
                    .previousEvents(p.events())
                    .distinctFeatures(a.features())
                    .topFeatureKey(topFeature.get(b.getId()))
                    .lastActivityAt(a.last())
                    .trend(bucketize(daily.get(b.getId()), cur))
                    .build());
        }
        out.sort(Comparator.comparingInt(BranchRow::getEvents).reversed()
                .thenComparing(BranchRow::getBranchName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private List<UserRow> userRows(
            Range cur, Range prev, Scope scope, String dim, List<User> users, Map<UUID, Branch> branchById) {
        Map<UUID, UserAgg> curAgg = userAgg(cur, scope, dim);
        Map<UUID, UserAgg> prevAgg = userAgg(prev, scope, dim);
        Map<UUID, String> topFeature = topFeatureBy("user_id", cur, scope, dim);
        List<UserRow> out = new ArrayList<>();
        for (User u : users) {
            UserAgg a = curAgg.getOrDefault(u.getId(), UserAgg.EMPTY);
            UserAgg p = prevAgg.getOrDefault(u.getId(), UserAgg.EMPTY);
            Branch b = u.getBranchId() != null ? branchById.get(u.getBranchId()) : null;
            out.add(UserRow.builder()
                    .userId(u.getId())
                    .name(u.getName() != null ? u.getName() : "—")
                    .email(u.getEmail() != null ? u.getEmail() : "—")
                    .role(u.getRole() != null ? u.getRole().name() : "UNKNOWN")
                    .branchId(u.getBranchId())
                    .branchName(b != null ? b.getName() : null)
                    .active(u.isActive())
                    .events(a.events())
                    .previousEvents(p.events())
                    .featuresUsed(a.features())
                    .activeDays(a.activeDays())
                    .topFeatureKey(topFeature.get(u.getId()))
                    .lastSeenAt(a.last())
                    .trend(List.of())
                    .build());
        }
        out.sort(Comparator.comparingInt(UserRow::getEvents).reversed()
                .thenComparing(UserRow::getName, String.CASE_INSENSITIVE_ORDER));
        return out.size() > 300 ? out.subList(0, 300) : out;
    }

    private List<UserRow> attachUserTrends(
            List<UserRow> rows, Map<UUID, Map<LocalDate, Integer>> daily, Range cur) {
        for (UserRow r : rows) {
            Map<LocalDate, Integer> d = daily.get(r.getUserId());
            if (d != null) {
                r.setTrend(bucketize(d, cur));
            }
        }
        return rows;
    }

    private List<MatrixCell> matrix(Range cur, Scope scope, String dim, Set<String> keys, String column, Set<UUID> only) {
        Where w = Where.of(cur, scope);
        String sql = "SELECT " + dim + " AS f, " + column + " AS col, COUNT(*) AS hits, MAX(occurred_at) AS last_at "
                + "FROM product_events WHERE " + w.sql + " AND " + column + " IS NOT NULL GROUP BY 1, 2";
        List<MatrixCell> out = new ArrayList<>();
        for (Object[] r : query(sql, (rs, i) -> new Object[] {
                rs.getString("f"), rs.getObject("col", UUID.class), rs.getInt("hits"), rs.getTimestamp("last_at")}, w.args)) {
            if (!keys.contains((String) r[0])) {
                continue;
            }
            UUID col = (UUID) r[1];
            if (only != null && !only.contains(col)) {
                continue;
            }
            out.add(MatrixCell.builder()
                    .featureKey((String) r[0])
                    .columnId(col)
                    .hits((Integer) r[2])
                    .lastUsedAt(toInstant((Timestamp) r[3]))
                    .build());
        }
        return out;
    }

    private List<ActivityEvent> recentActivity(
            Range cur, Scope scope, String dim, List<User> users, Map<UUID, Branch> branchById) {
        Where w = Where.of(cur, scope);
        String sql = "SELECT occurred_at, user_id, role, branch_id, portal, " + dim + " AS f "
                + "FROM product_events WHERE " + w.sql + " ORDER BY occurred_at DESC LIMIT " + RECENT_ACTIVITY;
        Map<UUID, String> names = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u.getName() != null ? u.getName() : "—", (a, b) -> a));
        return query(sql, (rs, i) -> {
            UUID userId = rs.getObject("user_id", UUID.class);
            UUID bId = rs.getObject("branch_id", UUID.class);
            Branch b = bId != null ? branchById.get(bId) : null;
            return ActivityEvent.builder()
                    .occurredAt(toInstant(rs.getTimestamp("occurred_at")))
                    .userId(userId)
                    .userName(userId != null ? names.get(userId) : null)
                    .role(rs.getString("role"))
                    .branchName(b != null ? b.getName() : null)
                    .portal(rs.getString("portal"))
                    .featureKey(rs.getString("f"))
                    .build();
        }, w.args);
    }

    // ------------------------------------------------------------------ health

    static Health health(Totals t, int platformFeatureCount, Range cur) {
        double adoption = t.getProvisionedUsers() > 0
                ? Math.min(1d, t.getActiveUsers() / (double) t.getProvisionedUsers())
                : 0d;
        int core = Math.max(1, Math.min(CORE_FEATURE_SET, platformFeatureCount));
        double breadth = Math.min(1d, t.getDistinctFeatures() / (double) core);
        double stickiness = t.getMau() > 0 ? Math.min(1d, t.getWau() / (double) t.getMau()) : 0d;
        double momentum;
        if (t.getPreviousEvents() == 0) {
            momentum = t.getEvents() > 0 ? 1d : 0d;
        } else {
            double delta = (t.getEvents() - t.getPreviousEvents()) / (double) t.getPreviousEvents();
            momentum = Math.max(0d, Math.min(1d, 0.5d + delta / 2d));
        }
        int score = (int) Math.round(100d * (0.35d * adoption + 0.25d * breadth + 0.20d * stickiness + 0.20d * momentum));

        String status;
        Instant dormantBefore = cur.toExclusive().minus(DORMANT_AFTER_DAYS, ChronoUnit.DAYS);
        if (t.getEvents() == 0 || t.getLastActivityAt() == null || t.getLastActivityAt().isBefore(dormantBefore)) {
            status = "DORMANT";
        } else if (t.getPreviousEvents() == 0) {
            status = "NEW";
        } else if (score >= 70) {
            status = "HEALTHY";
        } else if (score >= 45) {
            status = "WATCH";
        } else {
            status = "AT_RISK";
        }
        // A steep fall in a previously busy account is a risk regardless of the composite score.
        if (!"DORMANT".equals(status) && t.getPreviousEvents() >= 20 && t.getEvents() <= t.getPreviousEvents() * 0.5) {
            status = "AT_RISK";
            score = Math.min(score, 44);
        }
        // Past activity must not make a silent account look fine; the components stay visible to explain why.
        if ("DORMANT".equals(status)) {
            score = Math.min(score, 25);
        }
        return Health.builder()
                .score(score)
                .status(status)
                .adoption((int) Math.round(adoption * 100))
                .breadth((int) Math.round(breadth * 100))
                .stickiness((int) Math.round(stickiness * 100))
                .momentum((int) Math.round(momentum * 100))
                .build();
    }

    // ------------------------------------------------------------------ queries

    private Map<UUID, Agg> aggByTenant(Range range, Scope scope) {
        Where w = Where.of(range, scope);
        String dim = usageSql.featureDimensionSql();
        String sql = "SELECT tenant_id, COUNT(*) AS events, COUNT(DISTINCT user_id) AS users, "
                + "COUNT(DISTINCT " + dim + ") AS feats, MAX(occurred_at) AS last_at "
                + "FROM product_events WHERE " + w.sql + " AND tenant_id IS NOT NULL GROUP BY tenant_id";
        Map<UUID, Agg> out = new HashMap<>();
        for (Object[] r : query(sql, (rs, i) -> new Object[] {
                rs.getObject("tenant_id", UUID.class), rs.getInt("events"), rs.getInt("users"),
                rs.getInt("feats"), rs.getTimestamp("last_at")}, w.args)) {
            out.put((UUID) r[0], new Agg((Integer) r[1], (Integer) r[2], (Integer) r[3], toInstant((Timestamp) r[4])));
        }
        return out;
    }

    private Map<UUID, Integer> distinctUsersByTenant(Range range, Scope scope) {
        Where w = Where.of(range, scope);
        String sql = "SELECT tenant_id, COUNT(DISTINCT user_id) AS users FROM product_events WHERE " + w.sql
                + " AND tenant_id IS NOT NULL GROUP BY tenant_id";
        Map<UUID, Integer> out = new HashMap<>();
        for (Object[] r : query(sql, (rs, i) -> new Object[] {rs.getObject("tenant_id", UUID.class), rs.getInt("users")}, w.args)) {
            out.put((UUID) r[0], (Integer) r[1]);
        }
        return out;
    }

    private int distinctUsers(Range range, Scope scope) {
        Where w = Where.of(range, scope);
        String sql = "SELECT COUNT(DISTINCT user_id) AS users FROM product_events WHERE " + w.sql;
        List<Integer> rows = query(sql, (rs, i) -> rs.getInt("users"), w.args);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    private int countDistinctFeatures(Range range, Scope scope, String dim) {
        Where w = Where.of(range, scope);
        String sql = "SELECT COUNT(DISTINCT " + dim + ") AS feats FROM product_events WHERE " + w.sql;
        List<Integer> rows = query(sql, (rs, i) -> rs.getInt("feats"), w.args);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    private Map<UUID, Map<LocalDate, Integer>> dailyByTenant(Range range, Scope scope) {
        Where w = Where.of(range, scope);
        String sql = "SELECT tenant_id, " + dayExpr() + " AS d, COUNT(*) AS c FROM product_events WHERE " + w.sql
                + " AND tenant_id IS NOT NULL GROUP BY 1, 2";
        Map<UUID, Map<LocalDate, Integer>> out = new HashMap<>();
        for (Object[] r : query(sql, (rs, i) -> new Object[] {
                rs.getObject("tenant_id", UUID.class), rs.getDate("d").toLocalDate(), rs.getInt("c")}, w.args)) {
            out.computeIfAbsent((UUID) r[0], k -> new HashMap<>()).put((LocalDate) r[1], (Integer) r[2]);
        }
        return out;
    }

    private Map<UUID, Integer> provisionedByTenant(List<UUID> tenantIds) {
        String placeholders = tenantIds.stream().map(x -> "?").collect(Collectors.joining(","));
        String sql = "SELECT tenant_id, COUNT(*) AS c FROM users WHERE active = TRUE AND tenant_id IN (" + placeholders
                + ") GROUP BY tenant_id";
        Map<UUID, Integer> out = new HashMap<>();
        for (Object[] r : query(sql, (rs, i) -> new Object[] {rs.getObject("tenant_id", UUID.class), rs.getInt("c")},
                new ArrayList<Object>(tenantIds))) {
            out.put((UUID) r[0], (Integer) r[1]);
        }
        return out;
    }

    private Map<String, Integer> featureHits(Range range, Scope scope, String dim) {
        Where w = Where.of(range, scope);
        String sql = "SELECT " + dim + " AS f, COUNT(*) AS hits FROM product_events WHERE " + w.sql + " GROUP BY 1";
        Map<String, Integer> out = new HashMap<>();
        for (Object[] r : query(sql, (rs, i) -> new Object[] {rs.getString("f"), rs.getInt("hits")}, w.args)) {
            out.put((String) r[0], (Integer) r[1]);
        }
        return out;
    }

    private Map<String, Map<LocalDate, Integer>> featureDaily(Range range, Scope scope, String dim, Set<String> keys) {
        Where w = Where.of(range, scope);
        String sql = "SELECT " + dim + " AS f, " + dayExpr() + " AS d, COUNT(*) AS c FROM product_events WHERE " + w.sql
                + " GROUP BY 1, 2";
        Map<String, Map<LocalDate, Integer>> out = new HashMap<>();
        for (Object[] r : query(sql, (rs, i) -> new Object[] {
                rs.getString("f"), rs.getDate("d").toLocalDate(), rs.getInt("c")}, w.args)) {
            if (keys.contains((String) r[0])) {
                out.computeIfAbsent((String) r[0], k -> new HashMap<>()).put((LocalDate) r[1], (Integer) r[2]);
            }
        }
        return out;
    }

    private Map<UUID, BranchAgg> branchAgg(Range range, Scope scope, String dim) {
        Where w = Where.of(range, scope);
        String sql = "SELECT branch_id, COUNT(*) AS events, COUNT(DISTINCT user_id) AS users, "
                + "COUNT(DISTINCT " + dim + ") AS feats, MAX(occurred_at) AS last_at "
                + "FROM product_events WHERE " + w.sql + " AND branch_id IS NOT NULL GROUP BY branch_id";
        Map<UUID, BranchAgg> out = new HashMap<>();
        for (Object[] r : query(sql, (rs, i) -> new Object[] {
                rs.getObject("branch_id", UUID.class), rs.getInt("events"), rs.getInt("users"),
                rs.getInt("feats"), rs.getTimestamp("last_at")}, w.args)) {
            out.put((UUID) r[0], new BranchAgg((Integer) r[1], (Integer) r[2], (Integer) r[3], toInstant((Timestamp) r[4])));
        }
        return out;
    }

    private Map<UUID, Map<LocalDate, Integer>> branchDaily(Range range, Scope scope) {
        return dailyBy("branch_id", range, scope, null);
    }

    private Map<UUID, Map<LocalDate, Integer>> userDaily(Range range, Scope scope, Set<UUID> only) {
        return only.isEmpty() ? Map.of() : dailyBy("user_id", range, scope, only);
    }

    private Map<UUID, Map<LocalDate, Integer>> dailyBy(String column, Range range, Scope scope, Set<UUID> only) {
        Where w = Where.of(range, scope);
        String sql = "SELECT " + column + " AS col, " + dayExpr() + " AS d, COUNT(*) AS c FROM product_events WHERE " + w.sql
                + " AND " + column + " IS NOT NULL GROUP BY 1, 2";
        Map<UUID, Map<LocalDate, Integer>> out = new HashMap<>();
        for (Object[] r : query(sql, (rs, i) -> new Object[] {
                rs.getObject("col", UUID.class), rs.getDate("d").toLocalDate(), rs.getInt("c")}, w.args)) {
            UUID id = (UUID) r[0];
            if (only == null || only.contains(id)) {
                out.computeIfAbsent(id, k -> new HashMap<>()).put((LocalDate) r[1], (Integer) r[2]);
            }
        }
        return out;
    }

    private Map<UUID, UserAgg> userAgg(Range range, Scope scope, String dim) {
        Where w = Where.of(range, scope);
        String sql = "SELECT user_id, COUNT(*) AS events, COUNT(DISTINCT " + dim + ") AS feats, "
                + "COUNT(DISTINCT " + dayExpr() + ") AS days, MAX(occurred_at) AS last_at "
                + "FROM product_events WHERE " + w.sql + " AND user_id IS NOT NULL GROUP BY user_id";
        Map<UUID, UserAgg> out = new HashMap<>();
        for (Object[] r : query(sql, (rs, i) -> new Object[] {
                rs.getObject("user_id", UUID.class), rs.getInt("events"), rs.getInt("feats"),
                rs.getInt("days"), rs.getTimestamp("last_at")}, w.args)) {
            out.put((UUID) r[0], new UserAgg((Integer) r[1], (Integer) r[2], (Integer) r[3], toInstant((Timestamp) r[4])));
        }
        return out;
    }

    /** Most-used feature per branch or user (ties broken arbitrarily). */
    private Map<UUID, String> topFeatureBy(String column, Range range, Scope scope, String dim) {
        Where w = Where.of(range, scope);
        String sql = "SELECT col, f FROM (SELECT " + column + " AS col, " + dim + " AS f, "
                + "ROW_NUMBER() OVER (PARTITION BY " + column + " ORDER BY COUNT(*) DESC) AS rn "
                + "FROM product_events WHERE " + w.sql + " AND " + column + " IS NOT NULL GROUP BY 1, 2) ranked WHERE rn = 1";
        Map<UUID, String> out = new HashMap<>();
        for (Object[] r : query(sql, (rs, i) -> new Object[] {rs.getObject("col", UUID.class), rs.getString("f")}, w.args)) {
            out.put((UUID) r[0], (String) r[1]);
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private static String dayExpr() {
        return "(occurred_at AT TIME ZONE 'UTC')::date";
    }

    private <T> List<T> query(String sql, RowMapper<T> mapper, List<Object> args) {
        try {
            return jdbcTemplate.query(sql, mapper, args.toArray());
        } catch (DataAccessException ex) {
            log.warn("usage insights query failed: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * Collapses a per-day series into at most {@value #MAX_TREND_POINTS} buckets (zero-filled, oldest first) so the
     * UI can draw a sparkline for any range.
     */
    static List<Integer> bucketize(Map<LocalDate, Integer> perDay, Range range) {
        int days = range.days();
        int bucketDays = (int) Math.ceil(days / (double) MAX_TREND_POINTS);
        int buckets = (int) Math.ceil(days / (double) bucketDays);
        int[] counts = new int[buckets];
        if (perDay != null) {
            for (Map.Entry<LocalDate, Integer> e : perDay.entrySet()) {
                long offset = ChronoUnit.DAYS.between(range.fromDate(), e.getKey());
                if (offset >= 0 && offset < days) {
                    counts[(int) (offset / bucketDays)] += e.getValue();
                }
            }
        }
        List<Integer> out = new ArrayList<>(buckets);
        for (int c : counts) {
            out.add(c);
        }
        return out;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }

    private record Agg(int events, int users, int features, Instant last) {
        static final Agg EMPTY = new Agg(0, 0, 0, null);
    }

    private record BranchAgg(int events, int users, int features, Instant last) {
        static final BranchAgg EMPTY = new BranchAgg(0, 0, 0, null);
    }

    private record UserAgg(int events, int features, int activeDays, Instant last) {
        static final UserAgg EMPTY = new UserAgg(0, 0, 0, null);
    }

    /** Which events to read: a set of brands (portfolio), one brand (+ optional branch), or everything. */
    private record Scope(List<UUID> tenantIds, UUID branchId) {
        static Scope tenants(List<UUID> ids) {
            return new Scope(ids, null);
        }

        static Scope tenant(UUID tenantId, UUID branchId) {
            return new Scope(List.of(tenantId), branchId);
        }

        static Scope all() {
            return new Scope(null, null);
        }
    }

    private record Where(String sql, List<Object> args) {
        static Where of(Range range, Scope scope) {
            StringBuilder sql = new StringBuilder("occurred_at >= ? AND occurred_at < ?");
            List<Object> args = new ArrayList<>();
            args.add(Timestamp.from(range.from()));
            args.add(Timestamp.from(range.toExclusive()));
            if (scope.tenantIds() != null && !scope.tenantIds().isEmpty()) {
                sql.append(" AND tenant_id IN (")
                        .append(scope.tenantIds().stream().map(x -> "?").collect(Collectors.joining(",")))
                        .append(")");
                args.addAll(scope.tenantIds());
            }
            if (scope.branchId() != null) {
                sql.append(" AND branch_id = ?");
                args.add(scope.branchId());
            }
            return new Where(sql.toString(), args);
        }
    }

    /** Inclusive UTC date range, matching how the existing usage screens interpret the request instants. */
    record Range(Instant from, Instant toExclusive, LocalDate fromDate, LocalDate toDate, int days) {
        static Range of(Instant from, Instant to) {
            Instant safeTo = to != null ? to : Instant.now();
            Instant safeFrom = from != null ? from : safeTo.minus(28, ChronoUnit.DAYS);
            if (safeFrom.isAfter(safeTo)) {
                Instant tmp = safeFrom;
                safeFrom = safeTo;
                safeTo = tmp;
            }
            return ofDates(
                    safeFrom.atZone(ZoneOffset.UTC).toLocalDate(), safeTo.atZone(ZoneOffset.UTC).toLocalDate());
        }

        static Range ofDates(LocalDate fromDate, LocalDate toDate) {
            Instant from = fromDate.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant toExclusive = toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            int days = (int) ChronoUnit.DAYS.between(fromDate, toDate) + 1;
            return new Range(from, toExclusive, fromDate, toDate, Math.max(1, days));
        }

        Range previous() {
            return ofDates(fromDate.minusDays(days), fromDate.minusDays(1));
        }
    }
}
