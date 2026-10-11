package com.salonplatform.service;

import com.salonplatform.domain.entity.Tenant;
import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.repository.TenantRepository;
import com.salonplatform.domain.repository.UserRepository;
import com.salonplatform.dto.platform.PlatformTenantUsageDetailResponse;
import com.salonplatform.dto.platform.PlatformUsageSummaryResponse;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlatformUsageService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    public PlatformUsageSummaryResponse summary(Instant from, Instant to, boolean includeDemo) {
        SecurityUtils.assertPlatformAdmin();
        LocalDate fromDate = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate toDate = to.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate wauStart = toDate.minusDays(6);
        LocalDate mauStart = toDate.minusDays(27);

        List<Tenant> tenants = tenantRepository.findAll().stream()
                .filter(t -> includeDemo || !t.isDemo())
                .sorted(Comparator.comparing(Tenant::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        Map<UUID, Integer> provisioned = countProvisionedUsersByTenant();
        Map<UUID, Integer> wau = distinctUsersInRange(wauStart, toDate);
        Map<UUID, Integer> mau = distinctUsersInRange(mauStart, toDate);
        Map<UUID, Instant> lastActivity = lastActivityByTenant(fromDate, toDate);
        Map<UUID, List<PlatformUsageSummaryResponse.FeatureHit>> topFeatures = topFeaturesByTenant(fromDate, toDate, 5);

        List<PlatformUsageSummaryResponse.TenantUsageRow> rows = new ArrayList<>();
        for (Tenant tenant : tenants) {
            UUID id = tenant.getId();
            rows.add(PlatformUsageSummaryResponse.TenantUsageRow.builder()
                    .tenantId(id)
                    .tenantName(tenant.getName())
                    .tenantSlug(tenant.getSlug())
                    .demoTenant(tenant.isDemo())
                    .provisionedUsers(provisioned.getOrDefault(id, 0))
                    .wau(wau.getOrDefault(id, 0))
                    .mau(mau.getOrDefault(id, 0))
                    .lastActivityAt(lastActivity.get(id))
                    .topFeatures(topFeatures.getOrDefault(id, List.of()))
                    .build());
        }

        return PlatformUsageSummaryResponse.builder()
                .from(from)
                .to(to)
                .tenants(rows)
                .build();
    }

    public PlatformTenantUsageDetailResponse tenantDetail(UUID tenantId, Instant from, Instant to) {
        SecurityUtils.assertPlatformAdmin();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        LocalDate fromDate = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate toDate = to.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate wauStart = toDate.minusDays(6);
        LocalDate mauStart = toDate.minusDays(27);

        int provisioned = (int) userRepository.findAll().stream()
                .filter(u -> tenantId.equals(u.getTenantId()) && u.isActive())
                .count();

        List<PlatformTenantUsageDetailResponse.DailyActiveUsers> daily = jdbcTemplate.query("""
                        SELECT usage_date, active_users
                        FROM usage_daily_tenant
                        WHERE tenant_id = ? AND usage_date >= ? AND usage_date <= ?
                        ORDER BY usage_date
                        """,
                (rs, rowNum) -> PlatformTenantUsageDetailResponse.DailyActiveUsers.builder()
                        .date(rs.getDate("usage_date").toLocalDate())
                        .activeUsers(rs.getInt("active_users"))
                        .build(),
                tenantId,
                toSqlDate(fromDate),
                toSqlDate(toDate));

        List<PlatformUsageSummaryResponse.FeatureHit> features = jdbcTemplate.query("""
                        SELECT feature_key, SUM(hit_count) AS hits
                        FROM usage_daily_tenant_feature
                        WHERE tenant_id = ? AND usage_date >= ? AND usage_date <= ?
                        GROUP BY feature_key
                        ORDER BY hits DESC
                        LIMIT 15
                        """,
                (rs, rowNum) -> PlatformUsageSummaryResponse.FeatureHit.builder()
                        .featureKey(rs.getString("feature_key"))
                        .hitCount(rs.getInt("hits"))
                        .build(),
                tenantId,
                toSqlDate(fromDate),
                toSqlDate(toDate));

        Map<UUID, SnapshotRow> snapshots = loadSnapshots(tenantId);
        List<User> users = userRepository.findAll().stream()
                .filter(u -> tenantId.equals(u.getTenantId()))
                .sorted(Comparator.comparing(User::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<PlatformTenantUsageDetailResponse.UserUsageRow> userRows = users.stream()
                .map(u -> {
                    SnapshotRow snap = snapshots.get(u.getId());
                    int loginCount = snap != null ? snap.loginCount() : 0;
                    return PlatformTenantUsageDetailResponse.UserUsageRow.builder()
                            .userId(u.getId())
                            .name(u.getName())
                            .email(u.getEmail())
                            .role(u.getRole().name())
                            .branchId(u.getBranchId())
                            .active(u.isActive())
                            .lastSeenAt(snap != null ? snap.lastSeenAt() : null)
                            .lastPortal(snap != null ? snap.lastPortal() : null)
                            .lastFeatureKey(snap != null ? snap.lastFeatureKey() : null)
                            .loginCount(loginCount)
                            .daysActive28d(snap != null ? snap.daysActive28d() : 0)
                            .everLoggedIn(loginCount > 0 || (snap != null && snap.lastSeenAt() != null))
                            .build();
                })
                .toList();

        Map<UUID, Integer> wauMap = distinctUsersInRange(wauStart, toDate);
        Map<UUID, Integer> mauMap = distinctUsersInRange(mauStart, toDate);

        return PlatformTenantUsageDetailResponse.builder()
                .tenantId(tenantId)
                .tenantName(tenant.getName())
                .from(from)
                .to(to)
                .provisionedUsers(provisioned)
                .wau(wauMap.getOrDefault(tenantId, 0))
                .mau(mauMap.getOrDefault(tenantId, 0))
                .dailyActiveUsers(daily)
                .topFeatures(features)
                .users(userRows)
                .build();
    }

    private Map<UUID, Integer> countProvisionedUsersByTenant() {
        Map<UUID, Integer> counts = new HashMap<>();
        for (User user : userRepository.findAll()) {
            if (user.getTenantId() == null || !user.isActive()) {
                continue;
            }
            counts.merge(user.getTenantId(), 1, Integer::sum);
        }
        return counts;
    }

    private Map<UUID, Integer> distinctUsersInRange(LocalDate fromDate, LocalDate toDate) {
        return jdbcTemplate.query("""
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
                toSqlDate(fromDate),
                toSqlDate(toDate));
    }

    private Map<UUID, Instant> lastActivityByTenant(LocalDate fromDate, LocalDate toDate) {
        return jdbcTemplate.query("""
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
                toSqlDate(fromDate),
                toSqlDate(toDate));
    }

    private Map<UUID, List<PlatformUsageSummaryResponse.FeatureHit>> topFeaturesByTenant(
            LocalDate fromDate, LocalDate toDate, int limitPerTenant) {
        List<FeatureAggregate> aggregates = jdbcTemplate.query("""
                        SELECT tenant_id, feature_key, SUM(hit_count) AS hits
                        FROM usage_daily_tenant_feature
                        WHERE usage_date >= ? AND usage_date <= ?
                        GROUP BY tenant_id, feature_key
                        """,
                (rs, rowNum) -> new FeatureAggregate(
                        (UUID) rs.getObject("tenant_id"),
                        rs.getString("feature_key"),
                        rs.getInt("hits")),
                toSqlDate(fromDate),
                toSqlDate(toDate));

        Map<UUID, List<FeatureAggregate>> grouped = aggregates.stream()
                .collect(Collectors.groupingBy(FeatureAggregate::tenantId));

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
    }

    private Map<UUID, SnapshotRow> loadSnapshots(UUID tenantId) {
        return jdbcTemplate.query("""
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

    private record FeatureAggregate(UUID tenantId, String featureKey, int hits) {}

    private record SnapshotRow(
            Instant lastSeenAt,
            String lastPortal,
            String lastFeatureKey,
            int loginCount,
            int daysActive28d) {}

    private static java.sql.Date toSqlDate(LocalDate localDate) {
        return java.sql.Date.valueOf(localDate);
    }
}
