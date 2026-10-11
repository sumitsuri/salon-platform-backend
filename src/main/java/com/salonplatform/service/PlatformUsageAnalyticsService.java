package com.salonplatform.service;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.Tenant;
import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.enums.UserRole;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.TenantRepository;
import com.salonplatform.domain.repository.UserRepository;
import com.salonplatform.dto.platform.PlatformUsageAnalyticsResponse;
import com.salonplatform.dto.platform.PlatformUsagePortfolioResponse;
import com.salonplatform.dto.platform.PlatformUsageSummaryResponse;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.telemetry.ProductTelemetryReadiness;
import com.salonplatform.telemetry.ProductUsageRollupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformUsageAnalyticsService {

    /** Stable label for feature breakdown when {@code feature_key} was not persisted on older rows. */
    private static final String FEATURE_DIMENSION =
            "COALESCE(NULLIF(TRIM(feature_key), ''), NULLIF(TRIM(route), ''), event_name)";

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ProductTelemetryReadiness telemetryReadiness;
    private final ProductUsageRollupService rollupService;

    public PlatformUsagePortfolioResponse portfolio(
            Instant from, Instant to, boolean includeDemo, UUID tenantIdFilter, UUID branchIdFilter) {
        SecurityUtils.assertPlatformAdmin();
        InstantRange range = normalizeRange(from, to);
        List<Tenant> tenants = listTenants(includeDemo, tenantIdFilter);
        Set<UUID> demoIds = demoTenantIds();
        EventFilter platformProbe =
                EventFilter.platform(tenantIdFilter, branchIdFilter, demoIds, includeDemo);
        long rawPlatformEvents = countEventsForFilter(platformProbe, range);

        if (!telemetryReadiness.isEventsTableReady() && rawPlatformEvents == 0) {
            return portfolioFromRollups(range, tenants);
        }
        refreshRollupsForRange(range);
        try {
            PlatformUsagePortfolioResponse response =
                    portfolioWithEvents(range, tenants, includeDemo, tenantIdFilter, branchIdFilter, demoIds);
            if (response.getPlatformSummary().getTotalEvents() == 0 && rawPlatformEvents > 0) {
                log.warn(
                        "Portfolio aggregates were 0 but {} product_events matched the range; rebuilding response",
                        rawPlatformEvents);
                return portfolioWithEventsResilient(
                        range, tenants, includeDemo, tenantIdFilter, branchIdFilter, demoIds);
            }
            return response;
        } catch (Exception ex) {
            log.error("Platform usage portfolio query failed", ex);
            if (rawPlatformEvents > 0) {
                return portfolioWithEventsResilient(
                        range, tenants, includeDemo, tenantIdFilter, branchIdFilter, demoIds);
            }
            refreshRollupsForRange(range);
            return portfolioFromRollups(range, tenants);
        }
    }

    private void refreshRollupsForRange(InstantRange range) {
        try {
            int days = (int) ChronoUnit.DAYS.between(range.fromDate(), range.toDate()) + 1;
            rollupService.rollupRecentDays(Math.min(Math.max(days, 1), 7));
        } catch (Exception e) {
            log.warn("Could not refresh usage rollups for portfolio: {}", e.getMessage());
        }
    }

    private PlatformUsagePortfolioResponse portfolioWithEvents(
            InstantRange range,
            List<Tenant> tenants,
            boolean includeDemo,
            UUID tenantIdFilter,
            UUID branchIdFilter,
            Set<UUID> demoIds) {
        return buildPortfolioFromProductEvents(
                range, tenants, includeDemo, tenantIdFilter, branchIdFilter, demoIds, false);
    }

    /** Per-tenant failures must not zero out platform metrics when raw events exist. */
    private PlatformUsagePortfolioResponse portfolioWithEventsResilient(
            InstantRange range,
            List<Tenant> tenants,
            boolean includeDemo,
            UUID tenantIdFilter,
            UUID branchIdFilter,
            Set<UUID> demoIds) {
        return buildPortfolioFromProductEvents(
                range, tenants, includeDemo, tenantIdFilter, branchIdFilter, demoIds, true);
    }

    private PlatformUsagePortfolioResponse buildPortfolioFromProductEvents(
            InstantRange range,
            List<Tenant> tenants,
            boolean includeDemo,
            UUID tenantIdFilter,
            UUID branchIdFilter,
            Set<UUID> demoIds,
            boolean resilientTenants) {
        List<PlatformUsagePortfolioResponse.TenantRow> rows = new ArrayList<>();
        for (Tenant tenant : tenants) {
            EventFilter filter = EventFilter.forTenant(tenant.getId(), branchIdFilter, null, null, null);
            if (resilientTenants) {
                try {
                    rows.add(buildTenantRow(tenant, filter, range));
                } catch (Exception e) {
                    log.warn("Tenant usage row failed for {}: {}", tenant.getId(), e.getMessage());
                    rows.add(emptyTenantRow(tenant));
                }
            } else {
                rows.add(buildTenantRow(tenant, filter, range));
            }
        }

        EventFilter platformFilter = EventFilter.platform(tenantIdFilter, branchIdFilter, demoIds, includeDemo);
        UsageStats platformStats = loadUsageStats(platformFilter, range);

        return PlatformUsagePortfolioResponse.builder()
                .from(range.from())
                .to(range.to())
                .telemetryReady(true)
                .platformSummary(PlatformUsageAnalyticsResponse.UsageSummary.builder()
                        .provisionedUsers(rows.stream().mapToInt(PlatformUsagePortfolioResponse.TenantRow::getProvisionedUsers).sum())
                        .wau(platformStats.wau())
                        .mau(platformStats.mau())
                        .activeUsersInRange(platformStats.activeUsersInRange())
                        .totalEvents(platformStats.totalEvents())
                        .distinctFeatures(platformStats.distinctFeatures())
                        .build())
                .dailyActiveUsers(loadDailyPlatform(platformFilter, range))
                .topFeatures(safeTopFeatures(platformFilter, range, 12))
                .tenants(rows)
                .build();
    }

    private PlatformUsagePortfolioResponse.TenantRow buildTenantRow(
            Tenant tenant, EventFilter filter, InstantRange range) {
        UsageStats stats = loadUsageStats(filter, range);
        return PlatformUsagePortfolioResponse.TenantRow.builder()
                .tenantId(tenant.getId())
                .tenantName(tenant.getName())
                .tenantSlug(tenant.getSlug())
                .demoTenant(tenant.isDemo())
                .provisionedUsers(countProvisionedUsers(tenant.getId()))
                .wau(stats.wau())
                .mau(stats.mau())
                .activeUsersInRange(stats.activeUsersInRange())
                .totalEvents(stats.totalEvents())
                .lastActivityAt(stats.lastActivityAt())
                .topFeatures(safeTopFeatures(filter, range, 5))
                .build();
    }

    private PlatformUsagePortfolioResponse.TenantRow emptyTenantRow(Tenant tenant) {
        return PlatformUsagePortfolioResponse.TenantRow.builder()
                .tenantId(tenant.getId())
                .tenantName(tenant.getName())
                .tenantSlug(tenant.getSlug())
                .demoTenant(tenant.isDemo())
                .provisionedUsers(countProvisionedUsers(tenant.getId()))
                .wau(0)
                .mau(0)
                .activeUsersInRange(0)
                .totalEvents(0)
                .topFeatures(List.of())
                .build();
    }

    private List<PlatformUsageSummaryResponse.FeatureHit> safeTopFeatures(
            EventFilter filter, InstantRange range, int limit) {
        try {
            return loadTopFeatures(filter, range, limit);
        } catch (DataAccessException e) {
            log.warn("topFeatures query failed: {}", e.getMessage());
            return List.of();
        }
    }

    private long countEventsForFilter(EventFilter filter, InstantRange range) {
        try {
            FilterSql sql = filter.toSql();
            List<Object> args = rangeAndFilterArgs(range, sql);
            Long count = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*) FROM product_events
                            WHERE occurred_at >= ? AND occurred_at < ?
                            """ + sql.whereClause,
                    Long.class,
                    args.toArray());
            return count != null ? count : 0;
        } catch (DataAccessException e) {
            log.warn("countEventsForFilter failed: {}", e.getMessage());
            return 0;
        }
    }

    private static List<Object> rangeAndFilterArgs(InstantRange range, FilterSql sql) {
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.from(range.from()));
        args.add(Timestamp.from(range.toExclusive()));
        args.addAll(sql.args());
        return args;
    }

    /**
     * Serves portfolio metrics from hourly rollup tables ({@code usage_daily_*}) when live
     * {@code product_events} queries are unavailable. Same source as legacy {@code /usage/summary}.
     */
    private PlatformUsagePortfolioResponse portfolioFromRollups(InstantRange range, List<Tenant> tenants) {
        LocalDate fromDate = range.fromDate();
        LocalDate toDate = range.toDate();
        LocalDate wauStart = toDate.minusDays(6);
        LocalDate mauStart = toDate.minusDays(27);

        Map<UUID, Integer> wauByTenant = rollupDistinctUsersByTenant(wauStart, toDate);
        Map<UUID, Integer> mauByTenant = rollupDistinctUsersByTenant(mauStart, toDate);
        Map<UUID, Integer> activeInRange = rollupDistinctUsersByTenant(fromDate, toDate);
        Map<UUID, Integer> eventTotals = rollupEventTotalsByTenant(fromDate, toDate);
        Map<UUID, Instant> lastActivity = rollupLastActivityByTenant(fromDate, toDate);
        Map<UUID, List<PlatformUsageSummaryResponse.FeatureHit>> topByTenant =
                rollupTopFeaturesByTenant(fromDate, toDate, 5);

        List<PlatformUsagePortfolioResponse.TenantRow> rows = new ArrayList<>();
        for (Tenant tenant : tenants) {
            UUID id = tenant.getId();
            rows.add(PlatformUsagePortfolioResponse.TenantRow.builder()
                    .tenantId(id)
                    .tenantName(tenant.getName())
                    .tenantSlug(tenant.getSlug())
                    .demoTenant(tenant.isDemo())
                    .provisionedUsers(countProvisionedUsers(id))
                    .wau(wauByTenant.getOrDefault(id, 0))
                    .mau(mauByTenant.getOrDefault(id, 0))
                    .activeUsersInRange(activeInRange.getOrDefault(id, 0))
                    .totalEvents(eventTotals.getOrDefault(id, 0))
                    .lastActivityAt(lastActivity.get(id))
                    .topFeatures(topByTenant.getOrDefault(id, List.of()))
                    .build());
        }

        int platformWau = rollupDistinctUsersPlatform(wauStart, toDate);
        int platformMau = rollupDistinctUsersPlatform(mauStart, toDate);
        int platformActive = rollupDistinctUsersPlatform(fromDate, toDate);
        int platformEvents = rollupEventTotalPlatform(fromDate, toDate);
        int distinctFeatures = rollupDistinctFeaturesPlatform(fromDate, toDate);

        return PlatformUsagePortfolioResponse.builder()
                .from(range.from())
                .to(range.to())
                .telemetryReady(true)
                .platformSummary(PlatformUsageAnalyticsResponse.UsageSummary.builder()
                        .provisionedUsers(rows.stream().mapToInt(PlatformUsagePortfolioResponse.TenantRow::getProvisionedUsers).sum())
                        .wau(platformWau)
                        .mau(platformMau)
                        .activeUsersInRange(platformActive)
                        .totalEvents(platformEvents)
                        .distinctFeatures(distinctFeatures)
                        .build())
                .dailyActiveUsers(rollupPlatformDailySeries(range))
                .topFeatures(rollupPlatformTopFeatures(fromDate, toDate, 12))
                .tenants(rows)
                .build();
    }

    private Map<UUID, Integer> rollupDistinctUsersByTenant(LocalDate fromDate, LocalDate toDate) {
        try {
            return jdbcTemplate.query(
                    """
                            SELECT tenant_id, COUNT(DISTINCT user_id) AS cnt
                            FROM usage_daily_user
                            WHERE usage_date >= ? AND usage_date <= ? AND event_count > 0
                            GROUP BY tenant_id
                            """,
                    rs -> {
                        Map<UUID, Integer> map = new HashMap<>();
                        while (rs.next()) {
                            map.put((UUID) rs.getObject("tenant_id"), rs.getInt("cnt"));
                        }
                        return map;
                    },
                    java.sql.Date.valueOf(fromDate),
                    java.sql.Date.valueOf(toDate));
        } catch (DataAccessException ex) {
            log.debug("rollupDistinctUsersByTenant unavailable: {}", ex.getMessage());
            return Map.of();
        }
    }

    private int rollupDistinctUsersPlatform(LocalDate fromDate, LocalDate toDate) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(DISTINCT user_id)
                            FROM usage_daily_user
                            WHERE usage_date >= ? AND usage_date <= ? AND event_count > 0
                            """,
                    Integer.class,
                    java.sql.Date.valueOf(fromDate),
                    java.sql.Date.valueOf(toDate));
            return count != null ? count : 0;
        } catch (DataAccessException ex) {
            return 0;
        }
    }

    private Map<UUID, Integer> rollupEventTotalsByTenant(LocalDate fromDate, LocalDate toDate) {
        try {
            return jdbcTemplate.query(
                    """
                            SELECT tenant_id, COALESCE(SUM(event_count), 0) AS total
                            FROM usage_daily_user
                            WHERE usage_date >= ? AND usage_date <= ?
                            GROUP BY tenant_id
                            """,
                    rs -> {
                        Map<UUID, Integer> map = new HashMap<>();
                        while (rs.next()) {
                            map.put((UUID) rs.getObject("tenant_id"), rs.getInt("total"));
                        }
                        return map;
                    },
                    java.sql.Date.valueOf(fromDate),
                    java.sql.Date.valueOf(toDate));
        } catch (DataAccessException ex) {
            return Map.of();
        }
    }

    private int rollupEventTotalPlatform(LocalDate fromDate, LocalDate toDate) {
        try {
            Integer total = jdbcTemplate.queryForObject(
                    """
                            SELECT COALESCE(SUM(event_count), 0)
                            FROM usage_daily_user
                            WHERE usage_date >= ? AND usage_date <= ?
                            """,
                    Integer.class,
                    java.sql.Date.valueOf(fromDate),
                    java.sql.Date.valueOf(toDate));
            return total != null ? total : 0;
        } catch (DataAccessException ex) {
            return 0;
        }
    }

    private int rollupDistinctFeaturesPlatform(LocalDate fromDate, LocalDate toDate) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(DISTINCT feature_key)
                            FROM usage_daily_tenant_feature
                            WHERE usage_date >= ? AND usage_date <= ?
                            """,
                    Integer.class,
                    java.sql.Date.valueOf(fromDate),
                    java.sql.Date.valueOf(toDate));
            return count != null ? count : 0;
        } catch (DataAccessException ex) {
            return 0;
        }
    }

    private Map<UUID, Instant> rollupLastActivityByTenant(LocalDate fromDate, LocalDate toDate) {
        try {
            return jdbcTemplate.query(
                    """
                            SELECT tenant_id, MAX(last_event_at) AS last_at
                            FROM usage_daily_user
                            WHERE usage_date >= ? AND usage_date <= ?
                            GROUP BY tenant_id
                            """,
                    rs -> {
                        Map<UUID, Instant> map = new HashMap<>();
                        while (rs.next()) {
                            Timestamp ts = rs.getTimestamp("last_at");
                            if (ts != null) {
                                map.put((UUID) rs.getObject("tenant_id"), ts.toInstant());
                            }
                        }
                        return map;
                    },
                    java.sql.Date.valueOf(fromDate),
                    java.sql.Date.valueOf(toDate));
        } catch (DataAccessException ex) {
            return Map.of();
        }
    }

    private Map<UUID, List<PlatformUsageSummaryResponse.FeatureHit>> rollupTopFeaturesByTenant(
            LocalDate fromDate, LocalDate toDate, int limitPerTenant) {
        try {
            List<FeatureAggregate> aggregates = jdbcTemplate.query(
                    """
                            SELECT tenant_id, feature_key, SUM(hit_count) AS hits
                            FROM usage_daily_tenant_feature
                            WHERE usage_date >= ? AND usage_date <= ?
                            GROUP BY tenant_id, feature_key
                            """,
                    (rs, rowNum) -> new FeatureAggregate(
                            (UUID) rs.getObject("tenant_id"),
                            rs.getString("feature_key"),
                            rs.getInt("hits")),
                    java.sql.Date.valueOf(fromDate),
                    java.sql.Date.valueOf(toDate));

            Map<UUID, List<FeatureAggregate>> grouped =
                    aggregates.stream().collect(Collectors.groupingBy(FeatureAggregate::tenantId));
            Map<UUID, List<PlatformUsageSummaryResponse.FeatureHit>> result = new HashMap<>();
            for (Map.Entry<UUID, List<FeatureAggregate>> entry : grouped.entrySet()) {
                List<PlatformUsageSummaryResponse.FeatureHit> hits = entry.getValue().stream()
                        .sorted(Comparator.comparingInt(FeatureAggregate::hits).reversed())
                        .limit(limitPerTenant)
                        .map(a -> PlatformUsageSummaryResponse.FeatureHit.builder()
                                .featureKey(a.featureKey())
                                .hitCount(a.hits())
                                .build())
                        .toList();
                result.put(entry.getKey(), hits);
            }
            return result;
        } catch (DataAccessException ex) {
            return Map.of();
        }
    }

    private List<PlatformUsagePortfolioResponse.DailyPoint> rollupPlatformDailySeries(InstantRange range) {
        Map<LocalDate, DailyAgg> map = new HashMap<>();
        try {
            jdbcTemplate.query(
                    """
                            SELECT usage_date,
                                   COUNT(DISTINCT user_id) AS active_users,
                                   COALESCE(SUM(event_count), 0) AS event_count
                            FROM usage_daily_user
                            WHERE usage_date >= ? AND usage_date <= ?
                            GROUP BY usage_date
                            """,
                    rs -> {
                        map.put(
                                rs.getDate("usage_date").toLocalDate(),
                                new DailyAgg(rs.getInt("active_users"), rs.getInt("event_count")));
                    },
                    java.sql.Date.valueOf(range.fromDate()),
                    java.sql.Date.valueOf(range.toDate()));
        } catch (DataAccessException ex) {
            log.debug("rollupPlatformDailySeries unavailable: {}", ex.getMessage());
        }
        List<PlatformUsagePortfolioResponse.DailyPoint> out = new ArrayList<>();
        for (LocalDate day = range.fromDate(); !day.isAfter(range.toDate()); day = day.plusDays(1)) {
            DailyAgg agg = map.getOrDefault(day, DailyAgg.EMPTY);
            out.add(PlatformUsagePortfolioResponse.DailyPoint.builder()
                    .date(day)
                    .activeUsers(agg.activeUsers())
                    .eventCount(agg.eventCount())
                    .build());
        }
        return out;
    }

    private List<PlatformUsageSummaryResponse.FeatureHit> rollupPlatformTopFeatures(
            LocalDate fromDate, LocalDate toDate, int limit) {
        try {
            return jdbcTemplate.query(
                    """
                            SELECT feature_key, SUM(hit_count) AS hits
                            FROM usage_daily_tenant_feature
                            WHERE usage_date >= ? AND usage_date <= ?
                            GROUP BY feature_key
                            ORDER BY hits DESC
                            LIMIT
                            """
                            + Math.max(1, Math.min(limit, 100)),
                    (rs, rowNum) -> PlatformUsageSummaryResponse.FeatureHit.builder()
                            .featureKey(rs.getString("feature_key"))
                            .hitCount(rs.getInt("hits"))
                            .build(),
                    java.sql.Date.valueOf(fromDate),
                    java.sql.Date.valueOf(toDate));
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    private record FeatureAggregate(UUID tenantId, String featureKey, int hits) {}

    private List<Tenant> listTenants(boolean includeDemo, UUID tenantIdFilter) {
        return tenantRepository.findAll().stream()
                .filter(t -> includeDemo || !t.isDemo())
                .filter(t -> tenantIdFilter == null || t.getId().equals(tenantIdFilter))
                .sorted(Comparator.comparing(Tenant::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public PlatformUsageAnalyticsResponse tenantAnalytics(
            UUID tenantId,
            Instant from,
            Instant to,
            UUID branchId,
            UUID userId,
            UserRole role,
            String search) {
        SecurityUtils.assertPlatformAdmin();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        InstantRange range = normalizeRange(from, to);
        Map<UUID, Branch> branches = branchRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(Branch::getId, b -> b, (a, b) -> a));

        EventFilter filter = EventFilter.forTenant(tenantId, branchId, userId, role, search);
        long rawEvents = countEventsForFilter(filter, range);
        if (!telemetryReadiness.isEventsTableReady() && rawEvents == 0) {
            return tenantFallback(tenant, range, branches, search, role, branchId);
        }
        try {
            return buildTenantAnalyticsResponse(tenant, tenantId, range, filter, branches, branchId, userId, role, search);
        } catch (Exception ex) {
            log.error("Platform usage tenant analytics failed for {}", tenantId, ex);
            if (rawEvents > 0) {
                return buildTenantAnalyticsResponseResilient(
                        tenant, tenantId, range, filter, branches, branchId, userId, role, search);
            }
            return tenantFallback(tenant, range, branches, search, role, branchId);
        }
    }

    private PlatformUsageAnalyticsResponse buildTenantAnalyticsResponse(
            Tenant tenant,
            UUID tenantId,
            InstantRange range,
            EventFilter filter,
            Map<UUID, Branch> branches,
            UUID branchId,
            UUID userId,
            UserRole role,
            String search) {
        UsageStats stats = loadUsageStats(filter, range);
        return PlatformUsageAnalyticsResponse.builder()
                .from(range.from())
                .to(range.to())
                .telemetryReady(true)
                .tenantId(tenantId)
                .tenantName(tenant.getName())
                .summary(PlatformUsageAnalyticsResponse.UsageSummary.builder()
                        .provisionedUsers(countProvisionedUsers(tenantId))
                        .wau(stats.wau())
                        .mau(stats.mau())
                        .activeUsersInRange(stats.activeUsersInRange())
                        .totalEvents(stats.totalEvents())
                        .distinctFeatures(stats.distinctFeatures())
                        .build())
                .dailyActiveUsers(loadDailyTenant(filter, range))
                .featureUsage(loadFeatureUsage(filter, range))
                .branchUsage(loadBranchUsage(tenantId, range, branchId, userId, role, search, branches))
                .users(loadUserRows(tenantId, filter, range, branches, search, role, branchId, true))
                .build();
    }

    private PlatformUsageAnalyticsResponse buildTenantAnalyticsResponseResilient(
            Tenant tenant,
            UUID tenantId,
            InstantRange range,
            EventFilter filter,
            Map<UUID, Branch> branches,
            UUID branchId,
            UUID userId,
            UserRole role,
            String search) {
        UsageStats stats = loadUsageStats(filter, range);
        return PlatformUsageAnalyticsResponse.builder()
                .from(range.from())
                .to(range.to())
                .telemetryReady(true)
                .tenantId(tenantId)
                .tenantName(tenant.getName())
                .summary(PlatformUsageAnalyticsResponse.UsageSummary.builder()
                        .provisionedUsers(countProvisionedUsers(tenantId))
                        .wau(stats.wau())
                        .mau(stats.mau())
                        .activeUsersInRange(stats.activeUsersInRange())
                        .totalEvents(stats.totalEvents())
                        .distinctFeatures(stats.distinctFeatures())
                        .build())
                .dailyActiveUsers(safeDailyTenant(filter, range))
                .featureUsage(safeFeatureUsage(filter, range))
                .branchUsage(safeBranchUsage(tenantId, range, branchId, userId, role, search, branches))
                .users(safeUserRows(tenantId, filter, range, branches, search, role, branchId))
                .build();
    }

    private List<PlatformUsageAnalyticsResponse.DailyPoint> safeDailyTenant(EventFilter filter, InstantRange range) {
        try {
            return loadDailyTenant(filter, range);
        } catch (Exception e) {
            log.warn("dailyActiveUsers query failed: {}", e.getMessage());
            return zeroFillDays(range, Map.of());
        }
    }

    private List<PlatformUsageAnalyticsResponse.FeatureUsageRow> safeFeatureUsage(
            EventFilter filter, InstantRange range) {
        try {
            return loadFeatureUsage(filter, range);
        } catch (Exception e) {
            log.warn("featureUsage query failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<PlatformUsageAnalyticsResponse.BranchUsageRow> safeBranchUsage(
            UUID tenantId,
            InstantRange range,
            UUID branchId,
            UUID userId,
            UserRole role,
            String search,
            Map<UUID, Branch> branches) {
        try {
            return loadBranchUsage(tenantId, range, branchId, userId, role, search, branches);
        } catch (Exception e) {
            log.warn("branchUsage query failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<PlatformUsageAnalyticsResponse.UserUsageRow> safeUserRows(
            UUID tenantId,
            EventFilter filter,
            InstantRange range,
            Map<UUID, Branch> branches,
            String search,
            UserRole roleFilter,
            UUID branchFilter) {
        try {
            return loadUserRows(tenantId, filter, range, branches, search, roleFilter, branchFilter, true);
        } catch (Exception e) {
            log.warn("user usage rows failed: {}", e.getMessage());
            return List.of();
        }
    }

    private PlatformUsageAnalyticsResponse tenantFallback(
            Tenant tenant,
            InstantRange range,
            Map<UUID, Branch> branches,
            String search,
            UserRole roleFilter,
            UUID branchFilter) {
        EventFilter filter = EventFilter.forTenant(tenant.getId(), branchFilter, null, roleFilter, search);
        return PlatformUsageAnalyticsResponse.builder()
                .from(range.from())
                .to(range.to())
                .telemetryReady(true)
                .tenantId(tenant.getId())
                .tenantName(tenant.getName())
                .summary(PlatformUsageAnalyticsResponse.UsageSummary.builder()
                        .provisionedUsers(countProvisionedUsers(tenant.getId()))
                        .wau(0)
                        .mau(0)
                        .activeUsersInRange(0)
                        .totalEvents(0)
                        .distinctFeatures(0)
                        .build())
                .dailyActiveUsers(zeroFillDays(range, Map.of()))
                .featureUsage(List.of())
                .branchUsage(List.of())
                .users(loadUserRows(tenant.getId(), filter, range, branches, search, roleFilter, branchFilter, false))
                .build();
    }

    private List<PlatformUsageAnalyticsResponse.DailyPoint> loadDailyTenant(EventFilter filter, InstantRange range) {
        Map<LocalDate, DailyAgg> map = queryDailyAgg(filter, range);
        return zeroFillDays(range, map);
    }

    private List<PlatformUsagePortfolioResponse.DailyPoint> loadDailyPlatform(EventFilter filter, InstantRange range) {
        Map<LocalDate, DailyAgg> map = queryDailyAgg(filter, range);
        List<PlatformUsagePortfolioResponse.DailyPoint> out = new ArrayList<>();
        for (LocalDate day = range.fromDate(); !day.isAfter(range.toDate()); day = day.plusDays(1)) {
            DailyAgg agg = map.getOrDefault(day, DailyAgg.EMPTY);
            out.add(PlatformUsagePortfolioResponse.DailyPoint.builder()
                    .date(day)
                    .activeUsers(agg.activeUsers())
                    .eventCount(agg.eventCount())
                    .build());
        }
        return out;
    }

    private List<PlatformUsageAnalyticsResponse.DailyPoint> zeroFillDays(
            InstantRange range, Map<LocalDate, DailyAgg> map) {
        List<PlatformUsageAnalyticsResponse.DailyPoint> out = new ArrayList<>();
        for (LocalDate day = range.fromDate(); !day.isAfter(range.toDate()); day = day.plusDays(1)) {
            DailyAgg agg = map.getOrDefault(day, DailyAgg.EMPTY);
            out.add(PlatformUsageAnalyticsResponse.DailyPoint.builder()
                    .date(day)
                    .activeUsers(agg.activeUsers())
                    .eventCount(agg.eventCount())
                    .build());
        }
        return out;
    }

    private Map<LocalDate, DailyAgg> queryDailyAgg(EventFilter filter, InstantRange range) {
        FilterSql sql = filter.toSql();
        String query = """
                SELECT (occurred_at AT TIME ZONE 'UTC')::date AS usage_date,
                       COUNT(DISTINCT user_id) AS active_users,
                       COUNT(*) AS event_count
                FROM product_events
                WHERE occurred_at >= ? AND occurred_at < ?
                """ + sql.whereClause + """
                GROUP BY 1
                """;
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.from(range.from()));
        args.add(Timestamp.from(range.toExclusive()));
        args.addAll(sql.args());

        Map<LocalDate, DailyAgg> map = new HashMap<>();
        jdbcTemplate.query(query, rs -> {
            map.put(
                    rs.getDate("usage_date").toLocalDate(),
                    new DailyAgg(rs.getInt("active_users"), rs.getInt("event_count")));
        }, args.toArray());
        return map;
    }

    private List<PlatformUsageAnalyticsResponse.FeatureUsageRow> loadFeatureUsage(EventFilter filter, InstantRange range) {
        FilterSql sql = filter.toSql();
        String query = """
                SELECT """
                + FEATURE_DIMENSION
                + """
                 AS feature_key,
                       COUNT(*) AS hits,
                       COUNT(DISTINCT user_id) AS unique_users
                FROM product_events
                WHERE occurred_at >= ? AND occurred_at < ?
                """
                + sql.whereClause
                + """
                GROUP BY 1
                ORDER BY hits DESC
                LIMIT 100
                """;
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.from(range.from()));
        args.add(Timestamp.from(range.toExclusive()));
        args.addAll(sql.args());

        return jdbcTemplate.query(
                query,
                (rs, rowNum) -> PlatformUsageAnalyticsResponse.FeatureUsageRow.builder()
                        .featureKey(rs.getString("feature_key"))
                        .hitCount(rs.getInt("hits"))
                        .uniqueUsers(rs.getInt("unique_users"))
                        .build(),
                args.toArray());
    }

    private List<PlatformUsageAnalyticsResponse.BranchUsageRow> loadBranchUsage(
            UUID tenantId,
            InstantRange range,
            UUID branchId,
            UUID userId,
            UserRole role,
            String search,
            Map<UUID, Branch> branches) {
        EventFilter filter = EventFilter.forTenant(tenantId, branchId, userId, role, search);
        FilterSql sql = filter.toSql();
        String query = """
                SELECT branch_id,
                       COUNT(DISTINCT user_id) AS active_users,
                       COUNT(*) AS event_count
                FROM product_events
                WHERE occurred_at >= ? AND occurred_at < ?
                """ + sql.whereClause + """
                GROUP BY branch_id
                ORDER BY event_count DESC
                """;
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.from(range.from()));
        args.add(Timestamp.from(range.toExclusive()));
        args.addAll(sql.args());

        List<BranchAgg> aggs = jdbcTemplate.query(
                query,
                (rs, rowNum) -> new BranchAgg(
                        (UUID) rs.getObject("branch_id"),
                        rs.getInt("active_users"),
                        rs.getInt("event_count")),
                args.toArray());

        List<PlatformUsageAnalyticsResponse.BranchUsageRow> rows = new ArrayList<>();
        for (BranchAgg agg : aggs) {
            Branch branch = agg.branchId() != null ? branches.get(agg.branchId()) : null;
            String topFeature = safeTopFeatureForBranch(tenantId, agg.branchId(), range);
            rows.add(PlatformUsageAnalyticsResponse.BranchUsageRow.builder()
                    .branchId(agg.branchId())
                    .branchName(branch != null ? branch.getName() : "Unassigned")
                    .branchCode(branch != null ? branch.getCode() : "—")
                    .activeUsers(agg.activeUsers())
                    .eventCount(agg.eventCount())
                    .topFeatureKey(topFeature)
                    .build());
        }
        return rows;
    }

    private String safeTopFeatureForBranch(UUID tenantId, UUID branchId, InstantRange range) {
        try {
            return topFeatureForBranch(tenantId, branchId, range);
        } catch (DataAccessException e) {
            log.debug("topFeatureForBranch failed: {}", e.getMessage());
            return null;
        }
    }

    private String topFeatureForBranch(UUID tenantId, UUID branchId, InstantRange range) {
        String branchClause = branchId == null
                ? " AND branch_id IS NULL"
                : " AND branch_id = ?";
        String query = """
                SELECT """
                + FEATURE_DIMENSION
                + """
                 AS feature_key, COUNT(*) AS hits
                FROM product_events
                WHERE tenant_id = ? AND occurred_at >= ? AND occurred_at < ?
                """
                + branchClause
                + """
                GROUP BY 1
                ORDER BY hits DESC
                LIMIT 1
                """;
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.add(Timestamp.from(range.from()));
        args.add(Timestamp.from(range.toExclusive()));
        if (branchId != null) {
            args.add(branchId);
        }
        List<String> keys = jdbcTemplate.query(
                query,
                (rs, rowNum) -> rs.getString("feature_key"),
                args.toArray());
        return keys.isEmpty() ? null : keys.get(0);
    }

    private List<PlatformUsageAnalyticsResponse.UserUsageRow> loadUserRows(
            UUID tenantId,
            EventFilter filter,
            InstantRange range,
            Map<UUID, Branch> branches,
            String search,
            UserRole roleFilter,
            UUID branchFilter,
            boolean includeEventMetrics) {
        Map<UUID, UserEventAgg> eventAggs =
                includeEventMetrics ? loadUserEventAggs(filter, range) : Map.of();
        Map<UUID, SnapshotRow> snapshots = loadSnapshots(tenantId);

        String searchLower = search == null ? null : search.trim().toLowerCase();

        List<User> users = userRepository.findAll().stream()
                .filter(u -> tenantId.equals(u.getTenantId()))
                .filter(u -> branchFilter == null || branchFilter.equals(u.getBranchId()))
                .filter(u -> roleFilter == null || u.getRole() == roleFilter)
                .filter(u -> {
                    if (!StringUtils.hasText(searchLower)) {
                        return true;
                    }
                    String name = u.getName() != null ? u.getName().toLowerCase() : "";
                    String email = u.getEmail() != null ? u.getEmail().toLowerCase() : "";
                    return name.contains(searchLower) || email.contains(searchLower);
                })
                .sorted(Comparator.comparing(
                        u -> u.getName() != null ? u.getName() : "",
                        String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<PlatformUsageAnalyticsResponse.UserUsageRow> rows = new ArrayList<>();
        for (User user : users) {
            UserEventAgg agg = eventAggs.get(user.getId());
            SnapshotRow snap = snapshots.get(user.getId());
            int loginCount = snap != null ? snap.loginCount() : 0;
            Instant lastSeen = snap != null ? snap.lastSeenAt() : null;
            if (agg != null && agg.lastEventAt() != null) {
                lastSeen = lastSeen == null || agg.lastEventAt().isAfter(lastSeen) ? agg.lastEventAt() : lastSeen;
            }
            Branch branch = user.getBranchId() != null ? branches.get(user.getBranchId()) : null;
            boolean ever = loginCount > 0 || lastSeen != null || (agg != null && agg.eventCount() > 0);
            rows.add(PlatformUsageAnalyticsResponse.UserUsageRow.builder()
                    .userId(user.getId())
                    .name(user.getName() != null ? user.getName() : "—")
                    .email(user.getEmail() != null ? user.getEmail() : "—")
                    .role(user.getRole() != null ? user.getRole().name() : "UNKNOWN")
                    .branchId(user.getBranchId())
                    .branchName(branch != null ? branch.getName() : null)
                    .active(user.isActive())
                    .lastSeenAt(lastSeen)
                    .lastPortal(snap != null ? snap.lastPortal() : null)
                    .lastFeatureKey(snap != null ? snap.lastFeatureKey() : null)
                    .loginCount(loginCount)
                    .daysActive28d(snap != null ? snap.daysActive28d() : 0)
                    .eventsInRange(agg != null ? agg.eventCount() : 0)
                    .featuresUsedInRange(agg != null ? agg.distinctFeatures() : 0)
                    .everLoggedIn(ever)
                    .build());
        }
        return rows;
    }

    private Map<UUID, UserEventAgg> loadUserEventAggs(EventFilter filter, InstantRange range) {
        try {
            FilterSql sql = filter.toSql();
            String query = """
                    SELECT user_id,
                           COUNT(*) AS event_count,
                           COUNT(DISTINCT """
                           + FEATURE_DIMENSION
                           + """
                           ) AS distinct_features,
                           MAX(occurred_at) AS last_event_at
                    FROM product_events
                    WHERE occurred_at >= ? AND occurred_at < ?
                      AND user_id IS NOT NULL
                    """ + sql.whereClause + """
                    GROUP BY user_id
                    """;
            List<Object> args = new ArrayList<>();
            args.add(Timestamp.from(range.from()));
            args.add(Timestamp.from(range.toExclusive()));
            args.addAll(sql.args());

            Map<UUID, UserEventAgg> map = new HashMap<>();
            jdbcTemplate.query(query, rs -> {
                Timestamp last = rs.getTimestamp("last_event_at");
                map.put(
                        (UUID) rs.getObject("user_id"),
                        new UserEventAgg(
                                rs.getInt("event_count"),
                                rs.getInt("distinct_features"),
                                last != null ? last.toInstant() : null));
            }, args.toArray());
            return map;
        } catch (DataAccessException ex) {
            log.warn("product_events user aggregates unavailable: {}", ex.getMessage());
            return Map.of();
        }
    }

    private UsageStats loadUsageStats(EventFilter filter, InstantRange range) {
        FilterSql sql = filter.toSql();
        List<Object> args = rangeAndFilterArgs(range, sql);
        String rangeWhere = """
                FROM product_events
                WHERE occurred_at >= ? AND occurred_at < ?
                """ + sql.whereClause;

        Integer totalEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + rangeWhere, Integer.class, args.toArray());
        Integer activeUsers = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(DISTINCT user_id)
                        FROM product_events
                        WHERE occurred_at >= ? AND occurred_at < ?
                          AND user_id IS NOT NULL
                        """ + sql.whereClause,
                Integer.class,
                args.toArray());
        Instant lastAt = null;
        try {
            Timestamp lastTs = jdbcTemplate.queryForObject(
                    "SELECT MAX(occurred_at) " + rangeWhere, Timestamp.class, args.toArray());
            lastAt = timestampToInstant(lastTs);
        } catch (DataAccessException ignored) {
            // no rows in range
        }

        int distinctFeatures = 0;
        try {
            Integer df = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(DISTINCT """
                            + FEATURE_DIMENSION
                            + """
                            )
                            FROM product_events
                            WHERE occurred_at >= ? AND occurred_at < ?
                            """
                            + sql.whereClause,
                    Integer.class,
                    args.toArray());
            distinctFeatures = df != null ? df : 0;
        } catch (DataAccessException e) {
            log.debug("distinct feature_key unavailable: {}", e.getMessage());
        }

        int wau = 0;
        int mau = 0;
        try {
            wau = wauMauDistinct(filter, range.toDate().minusDays(6), range.toDate());
            mau = wauMauDistinct(filter, range.toDate().minusDays(27), range.toDate());
        } catch (DataAccessException e) {
            log.warn("WAU/MAU query failed: {}", e.getMessage());
        }

        return new UsageStats(
                activeUsers != null ? activeUsers : 0,
                wau,
                mau,
                totalEvents != null ? totalEvents : 0,
                distinctFeatures,
                lastAt);
    }

    private int wauMauDistinct(EventFilter filter, LocalDate fromDate, LocalDate toDate) {
        InstantRange sub = InstantRange.fromDates(fromDate, toDate);
        FilterSql sql = filter.toSql();
        String query = """
                SELECT COUNT(DISTINCT user_id)
                FROM product_events
                WHERE occurred_at >= ? AND occurred_at < ?
                  AND user_id IS NOT NULL
                """ + sql.whereClause;
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.from(sub.from()));
        args.add(Timestamp.from(sub.toExclusive()));
        args.addAll(sql.args());
        Integer count = jdbcTemplate.queryForObject(query, Integer.class, args.toArray());
        return count != null ? count : 0;
    }

    private List<PlatformUsageSummaryResponse.FeatureHit> loadTopFeatures(
            EventFilter filter, InstantRange range, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        FilterSql sql = filter.toSql();
        String query = """
                SELECT """
                + FEATURE_DIMENSION
                + """
                 AS feature_key, COUNT(*) AS hits
                FROM product_events
                WHERE occurred_at >= ? AND occurred_at < ?
                """
                + sql.whereClause
                + """
                GROUP BY 1
                ORDER BY hits DESC
                LIMIT """
                + safeLimit;
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.from(range.from()));
        args.add(Timestamp.from(range.toExclusive()));
        args.addAll(sql.args());

        return jdbcTemplate.query(
                query,
                (rs, rowNum) -> PlatformUsageSummaryResponse.FeatureHit.builder()
                        .featureKey(rs.getString("feature_key"))
                        .hitCount(rs.getInt("hits"))
                        .build(),
                args.toArray());
    }

    private int countProvisionedUsers(UUID tenantId) {
        return (int) userRepository.findAll().stream()
                .filter(u -> tenantId.equals(u.getTenantId()) && u.isActive())
                .count();
    }

    private Set<UUID> demoTenantIds() {
        return tenantRepository.findAll().stream()
                .filter(Tenant::isDemo)
                .map(Tenant::getId)
                .collect(Collectors.toSet());
    }

    private Map<UUID, SnapshotRow> loadSnapshots(UUID tenantId) {
        try {
            return loadSnapshotsUnchecked(tenantId);
        } catch (DataAccessException ex) {
            log.debug("user_usage_snapshot unavailable for tenant {}: {}", tenantId, ex.getMessage());
            return Map.of();
        }
    }

    private Map<UUID, SnapshotRow> loadSnapshotsUnchecked(UUID tenantId) {
        return jdbcTemplate.query(
                """
                        SELECT user_id, last_seen_at, last_portal, last_feature_key, login_count, days_active_28d
                        FROM user_usage_snapshot
                        WHERE tenant_id = ?
                        """,
                rs -> {
                    Map<UUID, SnapshotRow> map = new HashMap<>();
                    while (rs.next()) {
                        Timestamp lastSeen = rs.getTimestamp("last_seen_at");
                        map.put(
                                (UUID) rs.getObject("user_id"),
                                new SnapshotRow(
                                        lastSeen != null ? lastSeen.toInstant() : null,
                                        rs.getString("last_portal"),
                                        rs.getString("last_feature_key"),
                                        rs.getInt("login_count"),
                                        rs.getInt("days_active_28d")));
                    }
                    return map;
                },
                tenantId);
    }

    private static InstantRange normalizeRange(Instant from, Instant to) {
        Instant safeFrom = from != null ? from : Instant.now().minus(28, ChronoUnit.DAYS);
        Instant safeTo = to != null ? to : Instant.now();
        if (safeFrom.isAfter(safeTo)) {
            Instant tmp = safeFrom;
            safeFrom = safeTo;
            safeTo = tmp;
        }
        LocalDate fromDate = safeFrom.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate toDate = safeTo.atZone(ZoneOffset.UTC).toLocalDate();
        return InstantRange.fromDates(fromDate, toDate);
    }

    private static Instant timestampToInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }

    private record InstantRange(Instant from, Instant toExclusive, LocalDate fromDate, LocalDate toDate) {
        Instant to() {
            return toExclusive.minus(1, ChronoUnit.SECONDS);
        }

        static InstantRange fromDates(LocalDate fromDate, LocalDate toDate) {
            Instant from = fromDate.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant toExclusive = toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            return new InstantRange(from, toExclusive, fromDate, toDate);
        }
    }

    private record DailyAgg(int activeUsers, int eventCount) {
        static final DailyAgg EMPTY = new DailyAgg(0, 0);
    }

    private record UsageStats(
            int activeUsersInRange,
            int wau,
            int mau,
            int totalEvents,
            int distinctFeatures,
            Instant lastActivityAt) {
        static final UsageStats EMPTY = new UsageStats(0, 0, 0, 0, 0, null);
    }

    private record UserEventAgg(int eventCount, int distinctFeatures, Instant lastEventAt) {}

    private record BranchAgg(UUID branchId, int activeUsers, int eventCount) {}

    private record SnapshotRow(
            Instant lastSeenAt, String lastPortal, String lastFeatureKey, int loginCount, int daysActive28d) {}

    private record FilterSql(String whereClause, List<Object> args) {}

    private static final class EventFilter {
        private final UUID tenantId;
        private final UUID branchId;
        private final UUID userId;
        private final UserRole role;
        private final String search;
        private final Set<UUID> excludeTenantIds;

        private EventFilter(
                UUID tenantId,
                UUID branchId,
                UUID userId,
                UserRole role,
                String search,
                Set<UUID> excludeTenantIds) {
            this.tenantId = tenantId;
            this.branchId = branchId;
            this.userId = userId;
            this.role = role;
            this.search = search;
            this.excludeTenantIds = excludeTenantIds != null ? excludeTenantIds : Set.of();
        }

        static EventFilter forTenant(
                UUID tenantId, UUID branchId, UUID userId, UserRole role, String search) {
            return new EventFilter(tenantId, branchId, userId, role, search, Set.of());
        }

        static EventFilter platform(
                UUID tenantIdFilter, UUID branchId, Set<UUID> demoTenantIds, boolean includeDemo) {
            Set<UUID> exclude = includeDemo ? Set.of() : demoTenantIds;
            return new EventFilter(tenantIdFilter, branchId, null, null, null, exclude);
        }

        FilterSql toSql() {
            StringBuilder where = new StringBuilder();
            List<Object> args = new ArrayList<>();
            if (tenantId != null) {
                where.append(" AND tenant_id = ?");
                args.add(tenantId);
            }
            if (branchId != null) {
                where.append(" AND branch_id = ?");
                args.add(branchId);
            }
            if (userId != null) {
                where.append(" AND user_id = ?");
                args.add(userId);
            }
            if (role != null) {
                where.append(" AND role = ?");
                args.add(role.name());
            }
            if (StringUtils.hasText(search)) {
                where.append(" AND user_id IN (SELECT id FROM users WHERE tenant_id = COALESCE(?, tenant_id)"
                        + " AND (LOWER(name) LIKE ? OR LOWER(email) LIKE ?))");
                args.add(tenantId);
                String pattern = "%" + search.trim().toLowerCase() + "%";
                args.add(pattern);
                args.add(pattern);
            }
            if (!excludeTenantIds.isEmpty()) {
                where.append(" AND (tenant_id IS NULL OR tenant_id NOT IN (")
                        .append(excludeTenantIds.stream().map(x -> "?").collect(Collectors.joining(",")))
                        .append("))");
                args.addAll(excludeTenantIds);
            }
            return new FilterSql(where.toString(), args);
        }
    }
}
