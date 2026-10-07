package com.salonplatform.service;

import com.salonplatform.domain.entity.Invoice;
import com.salonplatform.domain.entity.Staff;
import com.salonplatform.domain.repository.InvoiceRepository;
import com.salonplatform.dto.staffportal.StaffPortalSalesInsightsResponse;
import com.salonplatform.dto.staffportal.StaffPortalSalesInsightsResponse.StaffSaleHistoryLine;
import com.salonplatform.dto.staffportal.StaffPortalSalesInsightsResponse.StaffSalesBoostSection;
import com.salonplatform.dto.staffportal.StaffPortalSalesInsightsResponse.StaffSalesBoostSuggestion;
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

    private final InvoiceRepository invoiceRepository;
    private final InvoiceSalesAggregationService invoiceSalesAggregationService;

    @Transactional(readOnly = true)
    public StaffPortalSalesInsightsResponse insights(Staff staff, int historyMonths) {
        int months = Math.max(1, Math.min(historyMonths, 6));
        LocalDate today = LocalDate.now(ZONE);
        LocalDate historyFrom = today.minusMonths(months).withDayOfMonth(1);
        LocalDate historyTo = today;
        LocalDate mtdStart = today.withDayOfMonth(1);

        var historyRangeStart = historyFrom.atStartOfDay(ZONE).toInstant();
        var historyRangeEnd = historyTo.plusDays(1).atStartOfDay(ZONE).toInstant();
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

        int daysInMonth = today.lengthOfMonth();
        int daysRemaining = Math.max(0, daysInMonth - today.getDayOfMonth());
        BigDecimal dailyNeeded = daysRemaining > 0
                ? gap.divide(BigDecimal.valueOf(daysRemaining), 0, RoundingMode.CEILING)
                : gap;

        StaffSalesBoostCatalog.Track track = StaffSalesBoostCatalog.trackForDesignation(staff.getDesignation());
        List<StaffSalesBoostSuggestion> suggestions = buildSuggestions(track, gap, rawLines, staff.getDesignation());

        String historyLabel = "Last " + months + " months";
        String periodLabel = historyFrom.format(RANGE) + " – " + historyTo.format(RANGE);

        return StaffPortalSalesInsightsResponse.builder()
                .historyFilterLabel(historyLabel + " · " + periodLabel)
                .historyFrom(historyFrom)
                .historyTo(historyTo)
                .history(history)
                .boost(StaffSalesBoostSection.builder()
                        .monthlyTarget(target)
                        .actualSalesMtd(actualMtd)
                        .gapToTarget(gap)
                        .daysRemaining(daysRemaining)
                        .dailyNeeded(dailyNeeded)
                        .trackLabel(trackLabel(track))
                        .suggestions(suggestions)
                        .build())
                .build();
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
            String designation) {
        if (gap.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of(StaffSalesBoostSuggestion.builder()
                    .serviceName("On target")
                    .typicalAmount(BigDecimal.ZERO)
                    .suggestedCount(0)
                    .estimatedRevenue(BigDecimal.ZERO)
                    .rationale("You’ve hit your monthly target — keep your ticket size up with add-on services.")
                    .build());
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
            String rationale = done == 0
                    ? "You haven’t logged this in the last 2 months — strong add-on for " + roleHint(designation) + "."
                    : done <= 2
                            ? "Only " + done + " in your recent history — room to grow this service."
                            : "Repeat winners — push " + suggested + " more to close your gap.";
            ranked.add(StaffSalesBoostSuggestion.builder()
                    .serviceName(entry.serviceName())
                    .typicalAmount(typical)
                    .suggestedCount(suggested)
                    .estimatedRevenue(est)
                    .rationale(rationale)
                    .build());
        }
        ranked.sort(Comparator.comparing(StaffSalesBoostSuggestion::getEstimatedRevenue).reversed());
        return ranked.stream().limit(5).toList();
    }

    private static String roleHint(String designation) {
        StaffSalesBoostCatalog.Track track = StaffSalesBoostCatalog.trackForDesignation(designation);
        return switch (track) {
            case HAIR -> "hair clients";
            case BEAUTY_SPA -> "beauty & spa guests";
            case GENERAL -> "your guests";
        };
    }
}
