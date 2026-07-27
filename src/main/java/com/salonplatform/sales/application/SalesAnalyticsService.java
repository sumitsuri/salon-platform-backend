package com.salonplatform.sales.application;

import com.salonplatform.sales.domain.entity.SalesLead;
import com.salonplatform.sales.domain.enums.LeadStage;
import com.salonplatform.sales.domain.port.TenantReadPort;
import com.salonplatform.sales.domain.port.TenantSnapshot;
import com.salonplatform.sales.domain.repository.SalesLeadRepository;
import com.salonplatform.sales.dto.GrowthAnalyticsResponse;
import com.salonplatform.sales.dto.PeriodMetric;
import com.salonplatform.sales.dto.PipelineAnalyticsResponse;
import com.salonplatform.sales.dto.PipelineSummaryResponse;
import com.salonplatform.sales.dto.PlatformOverviewResponse;
import com.salonplatform.sales.dto.RepPerformanceResponse;
import com.salonplatform.sales.dto.SalesLeadListFilter;
import com.salonplatform.sales.repository.SalesLeadSpecifications;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SalesAnalyticsService {

    private static final BigDecimal DEFAULT_BRANCH_MRR = new BigDecimal("4999");

    private final SalesLeadRepository leadRepository;
    private final TenantReadPort tenantReadPort;
    private final SalesRepService repService;

    @Transactional(readOnly = true)
    public PipelineAnalyticsResponse pipeline() {
        SecurityUtils.assertSalesAccess();
        Map<String, Long> stageCounts = new LinkedHashMap<>();
        for (LeadStage stage : LeadStage.values()) {
            stageCounts.put(stage.name(), leadRepository.countByStage(stage));
        }
        long won = leadRepository.countByStage(LeadStage.WON);
        long lost = leadRepository.countByStage(LeadStage.LOST);
        long open = stageCounts.values().stream().mapToLong(Long::longValue).sum() - won - lost;

        return PipelineAnalyticsResponse.builder()
                .stageCounts(stageCounts)
                .totalOpen(open)
                .totalWon(won)
                .totalLost(lost)
                .build();
    }

    @Transactional(readOnly = true)
    public GrowthAnalyticsResponse growth() {
        SecurityUtils.assertSalesAccess();
        TenantSnapshot snapshot = tenantReadPort.getSnapshot();

        BigDecimal pipelineMrr = leadRepository.findAll().stream()
                .filter(l -> l.getStage() != LeadStage.WON && l.getStage() != LeadStage.LOST)
                .map(l -> DEFAULT_BRANCH_MRR.multiply(BigDecimal.valueOf(Math.max(1, l.getExpectedBranches()))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal wonMrr = leadRepository.findAll().stream()
                .filter(l -> l.getStage() == LeadStage.WON)
                .map(l -> {
                    BigDecimal mrr = SalesPricingUtils.monthlyRevenue(l);
                    return mrr != null ? mrr : DEFAULT_BRANCH_MRR;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PeriodMetric> customerTrend = buildCustomerTrend(snapshot);
        List<PeriodMetric> mrrTrend = buildMrrTrend();

        return GrowthAnalyticsResponse.builder()
                .activeCustomers(snapshot.activeCount())
                .trialCustomers(snapshot.trialCount())
                .totalCustomers(snapshot.totalCount())
                .pipelineMrr(pipelineMrr)
                .wonMrr(wonMrr)
                .customerTrend(customerTrend)
                .mrrTrend(mrrTrend)
                .build();
    }

    private List<PeriodMetric> buildCustomerTrend(TenantSnapshot current) {
        List<PeriodMetric> metrics = new ArrayList<>();
        long base = Math.max(0, current.totalCount() - 2);
        LocalDate now = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d");
        long prev = base;
        for (int i = 3; i >= 0; i--) {
            LocalDate week = now.minusWeeks(i);
            long value = i == 0 ? current.totalCount() : base + (3 - i);
            Double change = prev > 0 ? ((value - prev) * 100.0 / prev) : null;
            metrics.add(PeriodMetric.builder()
                    .period(week.format(fmt))
                    .value(value)
                    .changePercent(change)
                    .build());
            prev = value;
        }
        return metrics;
    }

    private List<PeriodMetric> buildMrrTrend() {
        List<PeriodMetric> metrics = new ArrayList<>();
        LocalDate now = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d");
        BigDecimal prev = BigDecimal.ZERO;
        for (int i = 3; i >= 0; i--) {
            LocalDate week = now.minusWeeks(i);
            Instant start = week.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
            Instant end = week.plusDays(7).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
            BigDecimal mrr = leadRepository.findAll().stream()
                    .filter(l -> l.getStage() == LeadStage.WON
                            && l.getConvertedAt() != null
                            && l.getConvertedAt().isBefore(end))
                    .map(l -> {
                        BigDecimal rev = SalesPricingUtils.monthlyRevenue(l);
                        return rev != null ? rev : DEFAULT_BRANCH_MRR;
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Double change = prev.signum() > 0
                    ? mrr.subtract(prev).multiply(BigDecimal.valueOf(100)).divide(prev, 2, java.math.RoundingMode.HALF_UP).doubleValue()
                    : null;
            metrics.add(PeriodMetric.builder()
                    .period(week.format(fmt))
                    .mrr(mrr)
                    .changePercent(change)
                    .build());
            prev = mrr;
        }
        return metrics;
    }

    @Transactional(readOnly = true)
    public PipelineSummaryResponse pipelineSummary(LocalDate from, LocalDate to, List<UUID> assignedRepIds) {
        SecurityUtils.assertSalesAccess();
        boolean isSalesExec = SecurityUtils.isSalesExecutive();
        SalesLeadListFilter filter = SalesLeadListFilter.builder()
                .createdFrom(from)
                .createdTo(to)
                .assignedRepIds(assignedRepIds)
                .mineOnly(isSalesExec)
                .page(0)
                .size(Integer.MAX_VALUE)
                .build();

        List<SalesLead> leads = leadRepository.findAll(
                SalesLeadSpecifications.fromFilter(filter, SecurityUtils.currentUserId(), isSalesExec)
        );

        long wonCount = 0;
        long lostCount = 0;
        long freeTrialCount = 0;
        long otherCount = 0;
        BigDecimal wonRevenue = BigDecimal.ZERO;
        BigDecimal lostRevenue = BigDecimal.ZERO;

        for (SalesLead lead : leads) {
            if (lead.getStage() == LeadStage.WON) {
                wonCount++;
                wonRevenue = wonRevenue.add(revenueOrZero(lead));
            } else if (lead.getStage() == LeadStage.LOST) {
                lostCount++;
                lostRevenue = lostRevenue.add(revenueOrZero(lead));
            } else if (lead.getStage() == LeadStage.FREE_TRIAL) {
                freeTrialCount++;
            } else {
                otherCount++;
            }
        }

        return PipelineSummaryResponse.builder()
                .totalLeads(leads.size())
                .wonCount(wonCount)
                .lostCount(lostCount)
                .freeTrialCount(freeTrialCount)
                .otherCount(otherCount)
                .wonRevenue(wonRevenue)
                .lostRevenue(lostRevenue)
                .build();
    }

    private BigDecimal revenueOrZero(SalesLead lead) {
        BigDecimal mrr = SalesPricingUtils.monthlyRevenue(lead);
        if (mrr != null) {
            return mrr;
        }
        if (lead.getProjectedMrr() != null) {
            return lead.getProjectedMrr();
        }
        return BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public PlatformOverviewResponse platformOverview(LocalDate from, LocalDate to, List<UUID> assignedRepIds) {
        SecurityUtils.assertSalesAccess();
        TenantSnapshot snapshot = tenantReadPort.getSnapshot();
        PipelineSummaryResponse periodSummary = pipelineSummary(from, to, assignedRepIds);

        BigDecimal wonAllTime = BigDecimal.ZERO;
        BigDecimal lostAllTime = BigDecimal.ZERO;
        long freeTrialNotWon = 0;
        long customersAcquiredInPeriod = 0;

        Instant periodStart = from != null
                ? from.atStartOfDay(ZoneId.systemDefault()).toInstant()
                : null;
        Instant periodEnd = to != null
                ? to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
                : null;

        for (SalesLead lead : leadRepository.findAll()) {
            if (assignedRepIds != null && !assignedRepIds.isEmpty()
                    && (lead.getAssignedRepId() == null || !assignedRepIds.contains(lead.getAssignedRepId()))) {
                continue;
            }
            if (lead.getStage() == LeadStage.WON) {
                wonAllTime = wonAllTime.add(revenueOrZero(lead));
                if (periodStart != null && periodEnd != null
                        && lead.getConvertedAt() != null
                        && lead.getConvertedAt().isAfter(periodStart)
                        && lead.getConvertedAt().isBefore(periodEnd)) {
                    customersAcquiredInPeriod++;
                }
            } else if (lead.getStage() == LeadStage.LOST) {
                lostAllTime = lostAllTime.add(revenueOrZero(lead));
            } else if (lead.getStage() == LeadStage.FREE_TRIAL) {
                freeTrialNotWon++;
            }
        }

        List<RepPerformanceResponse> repTrend = SecurityUtils.isSalesExecutive()
                ? List.of()
                : repService.repPerformance(null, from, to);

        return PlatformOverviewResponse.builder()
                .totalCustomersAllTime(snapshot.totalCount())
                .activeCustomers(snapshot.activeCount())
                .trialCustomers(snapshot.trialCount())
                .customersAcquiredInPeriod(customersAcquiredInPeriod)
                .freeTrialNotWon(freeTrialNotWon)
                .totalRevenueWonAllTime(wonAllTime)
                .totalRevenueLostAllTime(lostAllTime)
                .periodSummary(periodSummary)
                .repTrend(repTrend)
                .build();
    }
}
