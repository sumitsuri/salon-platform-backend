package com.salonplatform.service;

import com.salonplatform.domain.entity.Invoice;
import com.salonplatform.domain.entity.Staff;
import com.salonplatform.domain.repository.InvoiceRepository;
import com.salonplatform.dto.staffportal.StaffPortalSalesInsightsResponse;
import com.salonplatform.dto.staffportal.StaffPortalSalesInsightsResponse.PeriodSummary;
import com.salonplatform.dto.staffportal.StaffPortalSalesInsightsResponse.ServiceContribution;
import com.salonplatform.dto.staffportal.StaffPortalSalesInsightsResponse.StaffSaleHistoryLine;
import com.salonplatform.dto.staffportal.StaffPortalSalesInsightsResponse.StaffSalesBoostSection;
import com.salonplatform.dto.staffportal.StaffPortalSalesInsightsResponse.StaffSalesBoostSuggestion;
import com.salonplatform.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffPortalSalesInsightsService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter RANGE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final int MAX_LOOKBACK_MONTHS = 2;

    private final InvoiceRepository invoiceRepository;
    private final InvoiceSalesAggregationService invoiceSalesAggregationService;

    @Transactional(readOnly = true)
    public StaffPortalSalesInsightsResponse insights(Staff staff, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate earliest = today.minusMonths(MAX_LOOKBACK_MONTHS).withDayOfMonth(1);
        LocalDate rangeFrom = from != null ? from : earliest;
        LocalDate rangeTo = to != null ? to : today;
        if (rangeFrom.isBefore(earliest)) {
            rangeFrom = earliest;
        }
        if (rangeTo.isAfter(today)) {
            rangeTo = today;
        }
        if (rangeTo.isBefore(rangeFrom)) {
            throw new BadRequestException("Invalid date range");
        }

        var historyRangeStart = rangeFrom.atStartOfDay(ZONE).toInstant();
        var historyRangeEnd = rangeTo.plusDays(1).atStartOfDay(ZONE).toInstant();
        List<Invoice> historyInvoices = invoiceRepository
                .findByTenantAndDateRange(staff.getTenantId(), historyRangeStart, historyRangeEnd)
                .stream()
                .filter(i -> staff.getBranchId().equals(i.getBranchId()))
                .collect(Collectors.toList());

        List<InvoiceSalesAggregationService.StaffSaleLineDetail> rawLines =
                invoiceSalesAggregationService.listSaleLinesForStaff(historyInvoices, staff.getId(), ZONE);
        List<StaffSaleHistoryLine> history = rawLines.stream()
                .map(l -> StaffSaleHistoryLine.builder()
                        .serviceDate(l.serviceDate())
                        .serviceName(l.serviceName())
                        .quantity(l.quantity())
                        .amount(l.amount())
                        .build())
                .toList();

        PeriodSummary periodSummary = buildPeriodSummary(rawLines);
        List<ServiceContribution> contributions = buildContributions(rawLines);
        String focusSummary = buildFocusSummary(contributions);

        LocalDate mtdStart = today.withDayOfMonth(1);
        var mtdRangeStart = mtdStart.atStartOfDay(ZONE).toInstant();
        var mtdRangeEnd = today.plusDays(1).atStartOfDay(ZONE).toInstant();
        List<Invoice> mtdInvoices = invoiceRepository
                .findByTenantAndDateRange(staff.getTenantId(), mtdRangeStart, mtdRangeEnd)
                .stream()
                .filter(i -> staff.getBranchId().equals(i.getBranchId()))
                .collect(Collectors.toList());
        var mtdAgg = invoiceSalesAggregationService.aggregateByStaff(mtdInvoices).get(staff.getId());
        BigDecimal actualMtd = mtdAgg != null ? mtdAgg.finalRevenue() : BigDecimal.ZERO;
        BigDecimal target = staff.getMonthlySalesTarget() != null ? staff.getMonthlySalesTarget() : BigDecimal.ZERO;
        BigDecimal gap = target.subtract(actualMtd).max(BigDecimal.ZERO);

        int daysRemaining = Math.max(0, today.lengthOfMonth() - today.getDayOfMonth());
        BigDecimal dailyNeeded = daysRemaining > 0
                ? gap.divide(BigDecimal.valueOf(daysRemaining), 0, RoundingMode.CEILING)
                : gap;

        StaffSalesBoostCatalog.Track track = StaffSalesBoostCatalog.trackForDesignation(staff.getDesignation());
        BigDecimal incentivePct = staff.getIncentivePercent() != null ? staff.getIncentivePercent() : BigDecimal.ZERO;
        List<StaffSalesBoostSuggestion> suggestions =
                buildSuggestions(track, gap, rawLines, staff.getDesignation(), contributions, incentivePct);

        String periodLabel = rangeFrom.format(RANGE) + " – " + rangeTo.format(RANGE);

        return StaffPortalSalesInsightsResponse.builder()
                .historyFilterLabel(periodLabel)
                .historyFrom(rangeFrom)
                .historyTo(rangeTo)
                .history(history)
                .periodSummary(periodSummary)
                .serviceContributions(contributions)
                .focusSummary(focusSummary)
                .boost(StaffSalesBoostSection.builder()
                        .monthlyTarget(target)
                        .actualSalesMtd(actualMtd)
                        .gapToTarget(gap)
                        .daysRemaining(daysRemaining)
                        .dailyNeeded(dailyNeeded)
                        .trackLabel(trackLabel(track))
                        .incentivePercent(incentivePct)
                        .suggestions(suggestions)
                        .build())
                .build();
    }

    private static PeriodSummary buildPeriodSummary(List<InvoiceSalesAggregationService.StaffSaleLineDetail> lines) {
        long count = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (var line : lines) {
            count += line.quantity();
            total = total.add(line.amount());
        }
        BigDecimal avg = count > 0
                ? total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return PeriodSummary.builder()
                .serviceCount(count)
                .totalSales(total)
                .avgTicket(avg)
                .build();
    }

    private static List<ServiceContribution> buildContributions(
            List<InvoiceSalesAggregationService.StaffSaleLineDetail> lines) {
        Map<String, BigDecimal> revenue = new HashMap<>();
        Map<String, Long> counts = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var line : lines) {
            revenue.merge(line.serviceName(), line.amount(), BigDecimal::add);
            counts.merge(line.serviceName(), (long) line.quantity(), Long::sum);
            total = total.add(line.amount());
        }
        BigDecimal totalFinal = total;
        return revenue.entrySet().stream()
                .map(e -> {
                    BigDecimal rev = e.getValue();
                    BigDecimal share = totalFinal.signum() > 0
                            ? rev.multiply(BigDecimal.valueOf(100)).divide(totalFinal, 1, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    return ServiceContribution.builder()
                            .serviceName(e.getKey())
                            .count(counts.getOrDefault(e.getKey(), 0L))
                            .revenue(rev)
                            .sharePercent(share)
                            .build();
                })
                .sorted(Comparator.comparing(ServiceContribution::getRevenue).reversed())
                .limit(8)
                .toList();
    }

    private static String buildFocusSummary(List<ServiceContribution> contributions) {
        if (contributions.isEmpty()) {
            return "No billed services in this period yet.";
        }
        String top = contributions.stream()
                .limit(3)
                .map(c -> c.getServiceName() + " (" + c.getSharePercent().stripTrailingZeros().toPlainString() + "%)")
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        return "Most of your sales in this period: " + top + ".";
    }

    private static String trackLabel(StaffSalesBoostCatalog.Track track) {
        return switch (track) {
            case HAIR -> "Hair services";
            case BEAUTY_SPA -> "Beauty & spa";
            case GENERAL -> "Salon services";
        };
    }

    private List<StaffSalesBoostSuggestion> buildSuggestions(
            StaffSalesBoostCatalog.Track track,
            BigDecimal gap,
            List<InvoiceSalesAggregationService.StaffSaleLineDetail> historyLines,
            String designation,
            List<ServiceContribution> contributions,
            BigDecimal incentivePercent) {
        List<StaffSalesBoostSuggestion> packages = buildPackageSuggestions(gap, incentivePercent);

        if (gap.compareTo(BigDecimal.ZERO) <= 0) {
            List<StaffSalesBoostSuggestion> onTarget = new ArrayList<>(packages);
            onTarget.add(StaffSalesBoostSuggestion.builder()
                    .serviceName("On target")
                    .typicalAmount(BigDecimal.ZERO)
                    .suggestedCount(0)
                    .estimatedRevenue(BigDecimal.ZERO)
                    .packageOffer(false)
                    .rationale("You’ve hit your monthly target — keep upselling packages and add-ons.")
                    .build());
            return onTarget;
        }

        Map<String, Long> counts = new HashMap<>();
        Map<String, BigDecimal> avgTicket = new HashMap<>();
        for (var line : historyLines) {
            String key = line.serviceName();
            counts.merge(key, 1L, Long::sum);
            avgTicket.merge(key, line.amount(), BigDecimal::add);
        }
        avgTicket.replaceAll((k, v) -> {
            long c = counts.getOrDefault(k, 1L);
            return v.divide(BigDecimal.valueOf(c), 0, RoundingMode.HALF_UP);
        });

        List<StaffSalesBoostCatalog.CatalogEntry> catalog = StaffSalesBoostCatalog.entriesFor(track);
        List<StaffSalesBoostSuggestion> ranked = new ArrayList<>();
        for (StaffSalesBoostCatalog.CatalogEntry entry : catalog) {
            long done = counts.getOrDefault(entry.serviceName(), 0L);
            BigDecimal typical = avgTicket.getOrDefault(entry.serviceName(), entry.typicalAmount());
            if (typical.signum() <= 0) {
                typical = entry.typicalAmount();
            }
            int suggested = typical.signum() > 0
                    ? gap.divide(typical, 0, RoundingMode.CEILING).intValue()
                    : 1;
            suggested = Math.min(Math.max(suggested, 1), 8);
            BigDecimal est = typical.multiply(BigDecimal.valueOf(suggested));
            String rationale = buildRationale(entry.serviceName(), done, suggested, contributions, designation);
            ranked.add(StaffSalesBoostSuggestion.builder()
                    .serviceName(entry.serviceName())
                    .typicalAmount(typical)
                    .suggestedCount(suggested)
                    .estimatedRevenue(est)
                    .packageOffer(false)
                    .rationale(rationale)
                    .build());
        }
        ranked.sort(Comparator.comparing(StaffSalesBoostSuggestion::getEstimatedRevenue).reversed());

        List<StaffSalesBoostSuggestion> combined = new ArrayList<>(packages);
        combined.addAll(ranked.stream().limit(4).toList());
        return combined;
    }

    private static List<StaffSalesBoostSuggestion> buildPackageSuggestions(BigDecimal gap, BigDecimal incentivePercent) {
        List<StaffSalesBoostSuggestion> out = new ArrayList<>();
        for (StaffSalesBoostCatalog.CatalogEntry entry : StaffSalesBoostCatalog.packageEntries()) {
            BigDecimal typical = entry.typicalAmount();
            int suggested = 1;
            if (gap.compareTo(BigDecimal.ZERO) > 0 && typical.signum() > 0) {
                suggested = Math.min(Math.max(gap.divide(typical, 0, RoundingMode.CEILING).intValue(), 1), 3);
            }
            BigDecimal est = typical.multiply(BigDecimal.valueOf(suggested));
            out.add(StaffSalesBoostSuggestion.builder()
                    .serviceName(entry.serviceName())
                    .typicalAmount(typical)
                    .suggestedCount(suggested)
                    .estimatedRevenue(est)
                    .packageOffer(true)
                    .rationale(packageRationale(typical, incentivePercent))
                    .build());
        }
        return out;
    }

    private static String packageRationale(BigDecimal packageAmount, BigDecimal incentivePercent) {
        if (incentivePercent == null || incentivePercent.compareTo(BigDecimal.ZERO) <= 0) {
            return "Packages boost your billed sales fast — one sale can equal several single services toward target.";
        }
        BigDecimal exampleBonus = packageAmount
                .multiply(incentivePercent)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        return "Counts toward target and incentive — e.g. one ₹"
                + packageAmount.setScale(0, RoundingMode.HALF_UP).toPlainString()
                + " package at "
                + incentivePercent.stripTrailingZeros().toPlainString()
                + "% adds about ₹"
                + exampleBonus.toPlainString()
                + " to your payout when you meet target.";
    }

    private static String buildRationale(
            String serviceName,
            long done,
            int suggested,
            List<ServiceContribution> contributions,
            String designation) {
        boolean isTop = contributions.stream()
                .limit(3)
                .anyMatch(c -> c.getServiceName().equalsIgnoreCase(serviceName));
        if (isTop) {
            return "Already a focus service — do " + suggested + " more this month to close your target gap.";
        }
        if (done == 0) {
            return "Not in your recent mix — strong " + roleHint(designation) + " add-on to diversify revenue.";
        }
        if (done <= 2) {
            return "Low volume recently — pushing this can balance your mix vs top sellers.";
        }
        return "Good fit for your role — repeat " + suggested + " times to move toward target.";
    }

    private static String roleHint(String designation) {
        StaffSalesBoostCatalog.Track track = StaffSalesBoostCatalog.trackForDesignation(designation);
        return switch (track) {
            case HAIR -> "hair";
            case BEAUTY_SPA -> "beauty & spa";
            case GENERAL -> "salon";
        };
    }
}
