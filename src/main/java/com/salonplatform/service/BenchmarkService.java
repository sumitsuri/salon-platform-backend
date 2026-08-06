package com.salonplatform.service;

import com.salonplatform.domain.entity.*;
import com.salonplatform.domain.enums.*;
import com.salonplatform.domain.repository.*;
import com.salonplatform.dto.analytics.*;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BenchmarkService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final int MIN_COHORT_SIZE = 3;
    private static final int MAX_LOCAL_COMPETITORS = 5;

    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final InvoiceRepository invoiceRepository;
    private final BookingLineItemRepository lineItemRepository;
    private final BranchExpenditureRepository expenditureRepository;
    private final InventoryMovementRepository movementRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final StaffRepository staffRepository;
    private final LocalCompetitorRepository localCompetitorRepository;

    public BenchmarkResponse getBenchmark(LocalDate startDate, LocalDate endDate, List<UUID> branchIds) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        LocalDate start = startDate != null ? startDate : LocalDate.now(ZONE).minusDays(59);
        LocalDate end = endDate != null ? endDate : LocalDate.now(ZONE);
        Instant rangeStart = start.atStartOfDay(ZONE).toInstant();
        Instant rangeEnd = end.plusDays(1).atStartOfDay(ZONE).toInstant();
        long periodDays = ChronoUnit.DAYS.between(start, end) + 1;

        List<Branch> branches = branchRepository.findByTenantId(tenantId).stream()
                .filter(b -> branchIds == null || branchIds.isEmpty() || branchIds.contains(b.getId()))
                .filter(b -> b.getStatus() == BranchStatus.ACTIVE)
                .toList();

        TenantMetricsSnapshot you = computeTenantMetrics(tenantId, branches, rangeStart, rangeEnd, periodDays);

        List<BenchmarkResponse.BranchRow> branchRankings = computeBranchRankings(
                tenantId, branches, rangeStart, rangeEnd, periodDays);

        List<TenantMetricsSnapshot> cohort = loadNetworkCohort(tenant, rangeStart, rangeEnd, periodDays);
        List<BenchmarkResponse.PeerRow> networkPeers = buildNetworkPeers(you, cohort, tenant.getName());

        List<BenchmarkResponse.LocalCompetitorRow> localCompetitors = loadLocalCompetitors(tenantId, branches);

        List<BenchmarkResponse.MetricComparison> allMetrics = buildMetricComparisons(you, cohort);
        List<BenchmarkResponse.MetricComparison> heroMetrics = allMetrics.stream().limit(5).toList();

        int aboveMedian = (int) allMetrics.stream()
                .filter(m -> "AHEAD".equals(m.getStatus()) || "ON_PAR".equals(m.getStatus()))
                .count();

        BigDecimal opportunity = allMetrics.stream()
                .map(BenchmarkResponse.MetricComparison::getGapToTopQuartile)
                .filter(Objects::nonNull)
                .filter(g -> g.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<BenchmarkResponse.PlaybookItem> playbook = buildPlaybook(allMetrics, you, branches.size(), periodDays);

        String periodLabel = start.format(DateTimeFormatter.ofPattern("d MMM"))
                + " – " + end.format(DateTimeFormatter.ofPattern("d MMM yyyy"));

        return BenchmarkResponse.builder()
                .periodLabel(periodLabel)
                .brandName(tenant.getName())
                .marketCity(tenant.getMarketCity())
                .cohortLabel(buildCohortLabel(tenant, cohort.size()))
                .cohortSize(cohort.size())
                .brandRank(rankInCohort(you, cohort))
                .metricsAboveMedian(aboveMedian)
                .totalMetrics(allMetrics.size())
                .estimatedMonthlyOpportunity(opportunity)
                .heroMetrics(heroMetrics)
                .allMetrics(allMetrics)
                .branchRankings(branchRankings)
                .networkPeers(networkPeers)
                .localCompetitors(localCompetitors)
                .playbook(playbook)
                .benchmarkOptIn(Boolean.TRUE.equals(tenant.getBenchmarkOptIn()))
                .build();
    }

    public BenchmarkSettingsResponse getSettings() {
        SecurityUtils.assertBrandAdminOrAbove();
        Tenant tenant = tenantRepository.findById(SecurityUtils.requireTenantId()).orElseThrow();
        return BenchmarkSettingsResponse.builder()
                .benchmarkOptIn(Boolean.TRUE.equals(tenant.getBenchmarkOptIn()))
                .marketCity(tenant.getMarketCity())
                .salonTier(tenant.getSalonTier() != null ? tenant.getSalonTier().name() : SalonTier.MID_MARKET.name())
                .build();
    }

    @Transactional
    public BenchmarkSettingsResponse updateSettings(UpdateBenchmarkSettingsRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        Tenant tenant = tenantRepository.findById(SecurityUtils.requireTenantId()).orElseThrow();
        if (request.getBenchmarkOptIn() != null) {
            tenant.setBenchmarkOptIn(request.getBenchmarkOptIn());
        }
        if (request.getMarketCity() != null && !request.getMarketCity().isBlank()) {
            tenant.setMarketCity(request.getMarketCity().trim());
        }
        if (request.getSalonTier() != null) {
            tenant.setSalonTier(SalonTier.valueOf(request.getSalonTier()));
        }
        tenantRepository.save(tenant);
        return getSettings();
    }

    public List<BenchmarkResponse.LocalCompetitorRow> listLocalCompetitors() {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        Map<UUID, String> branchNames = branchRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(Branch::getId, Branch::getName));
        return localCompetitorRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId).stream()
                .map(c -> toLocalRow(c, branchNames.get(c.getBranchId())))
                .toList();
    }

    @Transactional
    public BenchmarkResponse.LocalCompetitorRow createLocalCompetitor(UpsertLocalCompetitorRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        long count = localCompetitorRepository.countByTenantIdAndActiveTrue(tenantId);
        if (count >= MAX_LOCAL_COMPETITORS) {
            throw new IllegalArgumentException("Maximum " + MAX_LOCAL_COMPETITORS + " local competitors allowed");
        }
        LocalCompetitor entity = mapLocalCompetitor(new LocalCompetitor(), tenantId, request);
        entity = localCompetitorRepository.save(entity);
        String branchName = entity.getBranchId() != null
                ? branchRepository.findById(entity.getBranchId()).map(Branch::getName).orElse(null)
                : null;
        return toLocalRow(entity, branchName);
    }

    @Transactional
    public BenchmarkResponse.LocalCompetitorRow updateLocalCompetitor(UUID id, UpsertLocalCompetitorRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        LocalCompetitor entity = localCompetitorRepository.findById(id)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Competitor not found"));
        entity = localCompetitorRepository.save(mapLocalCompetitor(entity, tenantId, request));
        String branchName = entity.getBranchId() != null
                ? branchRepository.findById(entity.getBranchId()).map(Branch::getName).orElse(null)
                : null;
        return toLocalRow(entity, branchName);
    }

    @Transactional
    public void deleteLocalCompetitor(UUID id) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        LocalCompetitor entity = localCompetitorRepository.findById(id)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Competitor not found"));
        entity.setActive(false);
        localCompetitorRepository.save(entity);
    }

    // --- Internal metric computation ---

    private record TenantMetricsSnapshot(
            UUID tenantId,
            String tenantName,
            int branchCount,
            BigDecimal revenuePerBranchDay,
            BigDecimal avgTicket,
            BigDecimal visitsPerBranchDay,
            BigDecimal retailAttachPercent,
            BigDecimal netMarginPercent,
            BigDecimal repeatVisitRate,
            BigDecimal discountLeakagePercent,
            BigDecimal staffCostPercent,
            BigDecimal attendanceCompliancePercent,
            BigDecimal premiumServicePercent
    ) {}

    private TenantMetricsSnapshot computeTenantMetrics(
            UUID tenantId, List<Branch> branches, Instant rangeStart, Instant rangeEnd, long periodDays) {
        if (branches.isEmpty()) {
            return emptyMetrics(tenantId, "");
        }
        String name = tenantRepository.findById(tenantId).map(Tenant::getName).orElse("");
        int branchCount = branches.size();
        Set<UUID> branchSet = branches.stream().map(Branch::getId).collect(Collectors.toSet());

        List<Invoice> invoices = invoiceRepository.findByTenantAndDateRange(tenantId, rangeStart, rangeEnd).stream()
                .filter(i -> branchSet.contains(i.getBranchId()))
                .toList();

        BigDecimal revenue = invoices.stream().map(Invoice::getGrandTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        int visits = invoices.size();
        BigDecimal discounts = invoices.stream().map(Invoice::getDiscountAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal gross = revenue.add(discounts);

        long branchDays = branchCount * periodDays;
        BigDecimal revPerBranchDay = branchDays > 0
                ? revenue.divide(BigDecimal.valueOf(branchDays), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal visitsPerDay = branchDays > 0
                ? BigDecimal.valueOf(visits).divide(BigDecimal.valueOf(branchDays), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal avgTicket = visits > 0
                ? revenue.divide(BigDecimal.valueOf(visits), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal discountPct = gross.compareTo(BigDecimal.ZERO) > 0
                ? discounts.multiply(BigDecimal.valueOf(100)).divide(gross, 1, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        BigDecimal retailRevenue = computeRetailRevenue(tenantId, branchSet, rangeStart, rangeEnd);
        BigDecimal retailPct = revenue.compareTo(BigDecimal.ZERO) > 0
                ? retailRevenue.multiply(BigDecimal.valueOf(100)).divide(revenue, 1, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        BigDecimal netMargin = computeNetMarginPercent(tenantId, branchSet, rangeStart, rangeEnd, revenue);
        BigDecimal repeatRate = computeRepeatVisitRate(invoices);
        BigDecimal staffCostPct = computeStaffCostPercent(tenantId, branchSet, rangeStart, rangeEnd, revenue);
        BigDecimal attendancePct = computeAttendanceCompliance(
                tenantId, branchSet,
                rangeStart.atZone(ZONE).toLocalDate(),
                rangeEnd.atZone(ZONE).toLocalDate().minusDays(1));
        BigDecimal premiumPct = computePremiumServicePercent(invoices);

        return new TenantMetricsSnapshot(
                tenantId, name, branchCount, revPerBranchDay, avgTicket, visitsPerDay,
                retailPct, netMargin, repeatRate, discountPct, staffCostPct, attendancePct, premiumPct);
    }

    private TenantMetricsSnapshot emptyMetrics(UUID tenantId, String name) {
        return new TenantMetricsSnapshot(
                tenantId, name, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private BigDecimal computeRetailRevenue(UUID tenantId, Set<UUID> branchSet, Instant start, Instant end) {
        LocalDate dStart = start.atZone(ZONE).toLocalDate();
        LocalDate dEnd = end.atZone(ZONE).toLocalDate().minusDays(1);
        return movementRepository
                .findByTenantIdAndMovementDateBetweenOrderByMovementDateDescCreatedAtDesc(tenantId, dStart, dEnd)
                .stream()
                .filter(m -> branchSet.contains(m.getBranchId()))
                .filter(m -> m.getMovementType() == MovementType.RETAIL_SALE)
                .map(InventoryMovement::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal computeNetMarginPercent(
            UUID tenantId, Set<UUID> branchSet, Instant start, Instant end, BigDecimal revenue) {
        LocalDate monthStart = start.atZone(ZONE).toLocalDate().withDayOfMonth(1);
        LocalDate monthEnd = end.atZone(ZONE).toLocalDate().withDayOfMonth(1);
        BigDecimal expenses = expenditureRepository
                .findByTenantIdAndExpenseMonthBetweenAndActiveTrue(tenantId, monthStart, monthEnd)
                .stream()
                .filter(e -> branchSet.contains(e.getBranchId()))
                .map(BranchExpenditure::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (revenue.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return revenue.subtract(expenses).multiply(BigDecimal.valueOf(100))
                .divide(revenue, 1, RoundingMode.HALF_UP);
    }

    private BigDecimal computeStaffCostPercent(
            UUID tenantId, Set<UUID> branchSet, Instant start, Instant end, BigDecimal revenue) {
        LocalDate monthStart = start.atZone(ZONE).toLocalDate().withDayOfMonth(1);
        LocalDate monthEnd = end.atZone(ZONE).toLocalDate().withDayOfMonth(1);
        BigDecimal salary = expenditureRepository
                .findByTenantIdAndExpenseMonthBetweenAndActiveTrue(tenantId, monthStart, monthEnd)
                .stream()
                .filter(e -> branchSet.contains(e.getBranchId()))
                .filter(e -> e.getCategory() == ExpenditureCategory.EMPLOYEE_SALARY)
                .map(BranchExpenditure::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (revenue.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return salary.multiply(BigDecimal.valueOf(100)).divide(revenue, 1, RoundingMode.HALF_UP);
    }

    private BigDecimal computeRepeatVisitRate(List<Invoice> invoices) {
        if (invoices.isEmpty()) return BigDecimal.ZERO;
        Map<UUID, Long> byCustomer = invoices.stream()
                .collect(Collectors.groupingBy(Invoice::getCustomerId, Collectors.counting()));
        long repeat = byCustomer.values().stream().filter(c -> c >= 2).count();
        return BigDecimal.valueOf(repeat * 100.0 / byCustomer.size()).setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal computePremiumServicePercent(List<Invoice> invoices) {
        if (invoices.isEmpty()) return BigDecimal.ZERO;
        Set<UUID> bookingIds = invoices.stream().map(Invoice::getBookingId).collect(Collectors.toSet());
        BigDecimal premium = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (UUID bookingId : bookingIds) {
            for (BookingLineItem item : lineItemRepository.findByBookingId(bookingId)) {
                BigDecimal line = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                total = total.add(line);
                String name = item.getServiceName().toLowerCase();
                if (name.contains("color") || name.contains("facial") || name.contains("spa")
                        || name.contains("keratin") || name.contains("premium")) {
                    premium = premium.add(line);
                }
            }
        }
        if (total.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return premium.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    private BigDecimal computeAttendanceCompliance(
            UUID tenantId, Set<UUID> branchSet, LocalDate start, LocalDate end) {
        List<Staff> staff = staffRepository.findByTenantId(tenantId).stream()
                .filter(Staff::isActive)
                .filter(s -> branchSet.contains(s.getBranchId()))
                .toList();
        if (staff.isEmpty()) return BigDecimal.ZERO;
        long workdays = ChronoUnit.DAYS.between(start, end) + 1;
        long expected = staff.size() * workdays;
        if (expected == 0) return BigDecimal.ZERO;
        long present = attendanceRepository
                .findByTenantIdAndWorkDateBetweenOrderByEntryTimeDesc(tenantId, start, end)
                .stream()
                .filter(r -> branchSet.contains(r.getBranchId()))
                .filter(r -> r.getEntryTime() != null)
                .count();
        return BigDecimal.valueOf(Math.min(100, present * 100.0 / expected)).setScale(1, RoundingMode.HALF_UP);
    }

    private List<BenchmarkResponse.BranchRow> computeBranchRankings(
            UUID tenantId, List<Branch> branches, Instant rangeStart, Instant rangeEnd, long periodDays) {
        List<BenchmarkResponse.BranchRow> rows = new ArrayList<>();
        for (Branch branch : branches) {
            TenantMetricsSnapshot m = computeTenantMetrics(
                    tenantId, List.of(branch), rangeStart, rangeEnd, periodDays);
            rows.add(BenchmarkResponse.BranchRow.builder()
                    .branchId(branch.getId())
                    .branchName(branch.getName())
                    .revenuePerBranchDay(m.revenuePerBranchDay())
                    .avgTicket(m.avgTicket())
                    .visitsPerBranchDay(m.visitsPerBranchDay())
                    .netMarginPercent(m.netMarginPercent())
                    .retailAttachPercent(m.retailAttachPercent())
                    .repeatVisitRate(m.repeatVisitRate())
                    .discountLeakagePercent(m.discountLeakagePercent())
                    .build());
        }
        rows.sort(Comparator.comparing(BenchmarkResponse.BranchRow::getRevenuePerBranchDay).reversed());
        for (int i = 0; i < rows.size(); i++) {
            BenchmarkResponse.BranchRow row = rows.get(i);
            row.setRankInBrand(i + 1);
            row.setBranchCount(rows.size());
            row.setBrandPercentileLabel(percentileLabel(i + 1, rows.size()));
        }
        return rows;
    }

    private List<TenantMetricsSnapshot> loadNetworkCohort(
            Tenant tenant, Instant rangeStart, Instant rangeEnd, long periodDays) {
        if (!Boolean.TRUE.equals(tenant.getBenchmarkOptIn())) {
            return List.of();
        }
        String city = tenant.getMarketCity() != null ? tenant.getMarketCity() : "Bangalore";
        List<TenantMetricsSnapshot> cohort = new ArrayList<>();
        for (Tenant peer : tenantRepository.findByStatus(TenantStatus.ACTIVE)) {
            if (!Boolean.TRUE.equals(peer.getBenchmarkOptIn())) continue;
            if (peer.getMarketCity() == null || !peer.getMarketCity().equalsIgnoreCase(city)) continue;
            List<Branch> peerBranches = branchRepository.findByTenantId(peer.getId()).stream()
                    .filter(b -> b.getStatus() == BranchStatus.ACTIVE)
                    .toList();
            if (peerBranches.isEmpty()) continue;
            cohort.add(computeTenantMetrics(peer.getId(), peerBranches, rangeStart, rangeEnd, periodDays));
        }
        return cohort.size() >= MIN_COHORT_SIZE ? cohort : List.of();
    }

    private List<BenchmarkResponse.PeerRow> buildNetworkPeers(
            TenantMetricsSnapshot you, List<TenantMetricsSnapshot> cohort, String yourName) {
        if (cohort.isEmpty()) {
            return List.of(BenchmarkResponse.PeerRow.builder()
                    .peerLabel(yourName)
                    .tierLabel("Your brand")
                    .branchCount(you.branchCount())
                    .revenuePerBranchDay(you.revenuePerBranchDay())
                    .avgTicket(you.avgTicket())
                    .retailAttachPercent(you.retailAttachPercent())
                    .netMarginPercent(you.netMarginPercent())
                    .repeatVisitRate(you.repeatVisitRate())
                    .isYou(true)
                    .build());
        }
        List<TenantMetricsSnapshot> sorted = new ArrayList<>(cohort);
        sorted.sort(Comparator.comparing(TenantMetricsSnapshot::revenuePerBranchDay).reversed());
        List<BenchmarkResponse.PeerRow> rows = new ArrayList<>();
        char label = 'A';
        for (TenantMetricsSnapshot peer : sorted) {
            boolean isYou = peer.tenantId().equals(you.tenantId());
            rows.add(BenchmarkResponse.PeerRow.builder()
                    .peerLabel(isYou ? yourName : "Peer " + label++)
                    .tierLabel(isYou ? "Your brand" : "Network peer")
                    .branchCount(peer.branchCount())
                    .revenuePerBranchDay(peer.revenuePerBranchDay())
                    .avgTicket(peer.avgTicket())
                    .retailAttachPercent(peer.retailAttachPercent())
                    .netMarginPercent(peer.netMarginPercent())
                    .repeatVisitRate(peer.repeatVisitRate())
                    .isYou(isYou)
                    .build());
            if (!isYou && label > 'E') break;
        }
        return rows;
    }

    private List<BenchmarkResponse.MetricComparison> buildMetricComparisons(
            TenantMetricsSnapshot you, List<TenantMetricsSnapshot> cohort) {
        List<MetricDef> defs = metricDefinitions();
        List<BenchmarkResponse.MetricComparison> comparisons = new ArrayList<>();
        for (MetricDef def : defs) {
            BigDecimal yourVal = def.valueExtractor().apply(you);
            BigDecimal median = cohort.isEmpty() ? null : percentile(cohort, def, 50);
            BigDecimal topQ = cohort.isEmpty() ? null : percentile(cohort, def, 75);
            BigDecimal gapMedian = median != null ? yourVal.subtract(median) : null;
            BigDecimal gapTop = topQ != null ? yourVal.subtract(topQ) : null;
            String status = evaluateStatus(yourVal, median, def.higherIsBetter());
            Integer rank = cohort.isEmpty() ? null : percentileRank(cohort, you, def);
            comparisons.add(BenchmarkResponse.MetricComparison.builder()
                    .key(def.key())
                    .label(def.label())
                    .yourValue(yourVal)
                    .peerMedian(median)
                    .topQuartile(topQ)
                    .gapToMedian(gapMedian)
                    .gapToTopQuartile(gapTop != null && gapTop.compareTo(BigDecimal.ZERO) < 0
                            ? gapTop.abs() : BigDecimal.ZERO)
                    .unit(def.unit())
                    .direction(def.higherIsBetter() ? "HIGHER_BETTER" : "LOWER_BETTER")
                    .status(status)
                    .percentileRank(rank)
                    .build());
        }
        return comparisons;
    }

    private record MetricDef(
            String key, String label, String unit, boolean higherIsBetter,
            java.util.function.Function<TenantMetricsSnapshot, BigDecimal> valueExtractor) {}

    private List<MetricDef> metricDefinitions() {
        return List.of(
                new MetricDef("revPerBranchDay", "Revenue / branch / day", "INR", true, TenantMetricsSnapshot::revenuePerBranchDay),
                new MetricDef("avgTicket", "Average ticket", "INR", true, TenantMetricsSnapshot::avgTicket),
                new MetricDef("visitsPerDay", "Visits / branch / day", "", true, TenantMetricsSnapshot::visitsPerBranchDay),
                new MetricDef("retailAttach", "Retail attach", "%", true, TenantMetricsSnapshot::retailAttachPercent),
                new MetricDef("netMargin", "Net margin", "%", true, TenantMetricsSnapshot::netMarginPercent),
                new MetricDef("repeatVisit", "Repeat visit rate", "%", true, TenantMetricsSnapshot::repeatVisitRate),
                new MetricDef("premiumMix", "Premium service mix", "%", true, TenantMetricsSnapshot::premiumServicePercent),
                new MetricDef("attendance", "Attendance compliance", "%", true, TenantMetricsSnapshot::attendanceCompliancePercent),
                new MetricDef("discountLeak", "Discount leakage", "%", false, TenantMetricsSnapshot::discountLeakagePercent),
                new MetricDef("staffCost", "Staff cost ratio", "%", false, TenantMetricsSnapshot::staffCostPercent)
        );
    }

    private BigDecimal percentile(List<TenantMetricsSnapshot> cohort, MetricDef def, int pct) {
        List<BigDecimal> values = cohort.stream().map(def.valueExtractor()).sorted().toList();
        if (values.isEmpty()) return BigDecimal.ZERO;
        int idx = Math.min(values.size() - 1, (int) Math.ceil(pct / 100.0 * values.size()) - 1);
        idx = Math.max(0, idx);
        return values.get(idx);
    }

    private Integer percentileRank(List<TenantMetricsSnapshot> cohort, TenantMetricsSnapshot you, MetricDef def) {
        List<BigDecimal> values = cohort.stream().map(def.valueExtractor()).sorted().toList();
        BigDecimal yours = def.valueExtractor().apply(you);
        int below = 0;
        for (BigDecimal v : values) {
            if (v.compareTo(yours) < 0) below++;
        }
        return values.isEmpty() ? null : (int) Math.round(below * 100.0 / values.size());
    }

    private String evaluateStatus(BigDecimal yours, BigDecimal median, boolean higherIsBetter) {
        if (median == null) return "NO_COHORT";
        int cmp = yours.compareTo(median);
        if (cmp == 0) return "ON_PAR";
        boolean ahead = higherIsBetter ? cmp > 0 : cmp < 0;
        return ahead ? "AHEAD" : "BEHIND";
    }

    private Integer rankInCohort(TenantMetricsSnapshot you, List<TenantMetricsSnapshot> cohort) {
        if (cohort.isEmpty()) return null;
        List<TenantMetricsSnapshot> sorted = new ArrayList<>(cohort);
        sorted.sort(Comparator.comparing(TenantMetricsSnapshot::revenuePerBranchDay).reversed());
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).tenantId().equals(you.tenantId())) return i + 1;
        }
        return null;
    }

    private String buildCohortLabel(Tenant tenant, int size) {
        if (size < MIN_COHORT_SIZE) {
            return "Internal branch benchmark (network cohort forming — need " + MIN_COHORT_SIZE + "+ brands in "
                    + tenant.getMarketCity() + ")";
        }
        return size + " brands in " + tenant.getMarketCity() + " (anonymized network)";
    }

    private String percentileLabel(int rank, int total) {
        if (total <= 1) return "Only branch";
        double pct = (total - rank) * 100.0 / (total - 1);
        return "Top " + (int) Math.round(100 - pct) + "% in brand";
    }

    private List<BenchmarkResponse.PlaybookItem> buildPlaybook(
            List<BenchmarkResponse.MetricComparison> metrics, TenantMetricsSnapshot you, int branchCount, long periodDays) {
        List<BenchmarkResponse.PlaybookItem> items = new ArrayList<>();
        for (BenchmarkResponse.MetricComparison m : metrics) {
            if (!"BEHIND".equals(m.getStatus())) continue;
            switch (m.getKey()) {
                case "retailAttach" -> items.add(playbookItem(
                        "pb-retail", "HIGH", "Close the retail attach gap",
                        "Peers sell more retail per service visit. Enable stylist retail targets and post-service product scripts.",
                        "retailAttach", estimateRetailImpact(m, you, branchCount, periodDays),
                        "inventory", "Review inventory & retail"));
                case "avgTicket", "premiumMix" -> items.add(playbookItem(
                        "pb-atv", "HIGH", "Lift average ticket with premium services",
                        "Your ATV trails peers. Promote color, facial and package upsells during walk-in checkout.",
                        m.getKey(), estimateAtvImpact(m, you, branchCount, periodDays),
                        "campaigns", "Launch upsell campaign"));
                case "repeatVisit" -> items.add(playbookItem(
                        "pb-repeat", "MEDIUM", "Win back repeat visitors",
                        "Repeat rate is below peer median. Capture rebooking at checkout and run 60-day win-back WhatsApp.",
                        "repeatVisit", BigDecimal.valueOf(50000),
                        "campaigns", "Create win-back campaign"));
                case "netMargin" -> items.add(playbookItem(
                        "pb-margin", "HIGH", "Protect net margin",
                        "Margin trails peers — review staff cost ratio, rent and discount leakage branch by branch.",
                        "netMargin", BigDecimal.valueOf(75000),
                        "finance", "Open P&L dashboard"));
                case "discountLeak" -> items.add(playbookItem(
                        "pb-discount", "MEDIUM", "Tighten discount discipline",
                        "Discount leakage is above peers. Set manager approval thresholds for discounts above 10%.",
                        "discountLeak", BigDecimal.valueOf(30000),
                        "insights", "View discount insights"));
                case "attendance" -> items.add(playbookItem(
                        "pb-attendance", "MEDIUM", "Improve attendance compliance",
                        "Staff attendance trails peers — geofenced punch and grace policy review at weak branches.",
                        "attendance", BigDecimal.valueOf(40000),
                        "employees", "Review attendance"));
                default -> {}
            }
            if (items.size() >= 5) break;
        }
        return items;
    }

    private BenchmarkResponse.PlaybookItem playbookItem(
            String id, String severity, String title, String message, String metricKey,
            BigDecimal impact, String module, String actionLabel) {
        return BenchmarkResponse.PlaybookItem.builder()
                .id(id)
                .severity(severity)
                .title(title)
                .message(message)
                .metricKey(metricKey)
                .estimatedMonthlyImpact(impact)
                .actionModule(module)
                .actionLabel(actionLabel)
                .build();
    }

    private BigDecimal estimateRetailImpact(
            BenchmarkResponse.MetricComparison m, TenantMetricsSnapshot you, int branches, long days) {
        if (m.getGapToMedian() == null) return BigDecimal.valueOf(50000);
        BigDecimal dailyGap = you.revenuePerBranchDay()
                .multiply(m.getGapToMedian().abs())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return dailyGap.multiply(BigDecimal.valueOf(branches * Math.min(days, 30)))
                .setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal estimateAtvImpact(
            BenchmarkResponse.MetricComparison m, TenantMetricsSnapshot you, int branches, long days) {
        if (m.getGapToMedian() == null) return BigDecimal.valueOf(60000);
        BigDecimal visits = you.visitsPerBranchDay().multiply(BigDecimal.valueOf(branches * days));
        return m.getGapToMedian().abs().multiply(visits).divide(BigDecimal.valueOf(Math.max(days, 1) / 30.0 + 1), 0, RoundingMode.HALF_UP);
    }

    private List<BenchmarkResponse.LocalCompetitorRow> loadLocalCompetitors(UUID tenantId, List<Branch> branches) {
        Map<UUID, String> branchNames = branches.stream()
                .collect(Collectors.toMap(Branch::getId, Branch::getName));
        return localCompetitorRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId).stream()
                .map(c -> toLocalRow(c, c.getBranchId() != null ? branchNames.get(c.getBranchId()) : null))
                .toList();
    }

    private BenchmarkResponse.LocalCompetitorRow toLocalRow(LocalCompetitor c, String branchName) {
        return BenchmarkResponse.LocalCompetitorRow.builder()
                .id(c.getId())
                .name(c.getName())
                .competitorType(c.getCompetitorType().name())
                .branchId(c.getBranchId())
                .branchName(branchName)
                .revenuePerBranchDay(c.getRevenuePerBranchDay())
                .avgTicket(c.getAvgTicket())
                .retailAttachPercent(c.getRetailAttachPercent())
                .netMarginPercent(c.getNetMarginPercent())
                .repeatVisitRate(c.getRepeatVisitRate())
                .address(c.getAddress())
                .notes(c.getNotes())
                .googleRating(c.getGoogleRating())
                .googleReviewCount(c.getGoogleReviewCount())
                .gbpPhotoCount(c.getGbpPhotoCount())
                .gbpVideoCount(c.getGbpVideoCount())
                .gbpHasPhone(c.getGbpHasPhone())
                .estimatedSearchRank(c.getEstimatedSearchRank())
                .build();
    }

    private LocalCompetitor mapLocalCompetitor(
            LocalCompetitor entity, UUID tenantId, UpsertLocalCompetitorRequest request) {
        entity.setTenantId(tenantId);
        entity.setName(request.getName());
        if (request.getCompetitorType() != null) {
            entity.setCompetitorType(CompetitorType.valueOf(request.getCompetitorType()));
        }
        entity.setBranchId(request.getBranchId());
        entity.setAddress(request.getAddress());
        entity.setNotes(request.getNotes());
        entity.setRevenuePerBranchDay(request.getRevenuePerBranchDay());
        entity.setAvgTicket(request.getAvgTicket());
        entity.setRetailAttachPercent(request.getRetailAttachPercent());
        entity.setNetMarginPercent(request.getNetMarginPercent());
        entity.setRepeatVisitRate(request.getRepeatVisitRate());
        if (request.getGoogleRating() != null) entity.setGoogleRating(request.getGoogleRating());
        if (request.getGoogleReviewCount() != null) entity.setGoogleReviewCount(request.getGoogleReviewCount());
        if (request.getGbpPhotoCount() != null) entity.setGbpPhotoCount(request.getGbpPhotoCount());
        if (request.getGbpVideoCount() != null) entity.setGbpVideoCount(request.getGbpVideoCount());
        if (request.getGbpHasPhone() != null) entity.setGbpHasPhone(request.getGbpHasPhone());
        if (request.getEstimatedSearchRank() != null) entity.setEstimatedSearchRank(request.getEstimatedSearchRank());
        entity.setActive(true);
        return entity;
    }
}
