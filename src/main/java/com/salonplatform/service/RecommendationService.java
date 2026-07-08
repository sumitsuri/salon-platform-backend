package com.salonplatform.service;

import com.salonplatform.domain.enums.UserRole;
import com.salonplatform.dto.analytics.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    public RecommendationsResponse generate(DashboardResponse dashboard, UserRole role) {
        List<Recommendation> brandWide = role == UserRole.BRAND_ADMIN || role == UserRole.PLATFORM_SUPER_ADMIN
                ? generateBrandWide(dashboard)
                : List.of();

        List<BranchRecommendations> branches = dashboard.getBranchStats().stream()
                .map(branch -> {
                    BranchTrend trend = dashboard.getBranchTrends().stream()
                            .filter(t -> t.getBranchId().equals(branch.getBranchId()))
                            .findFirst()
                            .orElse(null);
                    List<StaffStats> branchStaff = branch.getTopStaff() != null
                            ? branch.getTopStaff() : List.of();
                    List<ServiceStats> branchServices = branch.getTopServices() != null
                            ? branch.getTopServices() : List.of();

                    List<Recommendation> items = generateBranchRecommendations(
                            branch, trend, branchStaff, branchServices, dashboard);

                    return BranchRecommendations.builder()
                            .branchId(branch.getBranchId())
                            .branchName(branch.getBranchName())
                            .items(items)
                            .build();
                })
                .filter(b -> !b.getItems().isEmpty())
                .collect(Collectors.toList());

        return RecommendationsResponse.builder()
                .brandWide(brandWide)
                .branches(branches)
                .build();
    }

    private List<Recommendation> generateBrandWide(DashboardResponse dashboard) {
        List<Recommendation> items = new ArrayList<>();

        if (dashboard.getBranchStats().size() >= 2) {
            BranchStats top = dashboard.getBranchStats().get(0);
            BranchStats bottom = dashboard.getBranchStats().get(dashboard.getBranchStats().size() - 1);
            if (bottom.getRevenue().compareTo(BigDecimal.ZERO) > 0
                    && top.getRevenue().compareTo(bottom.getRevenue().multiply(BigDecimal.valueOf(1.5))) > 0) {
                BigDecimal gap = top.getRevenue().subtract(bottom.getRevenue())
                        .multiply(BigDecimal.valueOf(100))
                        .divide(bottom.getRevenue(), 0, RoundingMode.HALF_UP);
                items.add(rec("brand-gap", "SALES", "HIGH",
                        "Large performance gap between branches",
                        top.getBranchName() + " is outperforming " + bottom.getBranchName()
                                + " by " + gap + "%. Review staffing, promotions, and service mix at the weaker branch.",
                        null, null, "Revenue gap", gap + "%"));
            }
        }

        if (dashboard.getTotalVisits() > 0 && dashboard.getTotalRevenue().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal discountRate = dashboard.getTotalDiscounts()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(dashboard.getTotalRevenue(), 1, RoundingMode.HALF_UP);
            if (discountRate.compareTo(BigDecimal.valueOf(12)) > 0) {
                items.add(rec("brand-discount", "DISCOUNTS", "MEDIUM",
                        "Brand-wide discount rate is elevated",
                        "Discounts are " + discountRate + "% of revenue. Tighten approval thresholds or train managers on value-selling.",
                        null, null, "Discount rate", discountRate + "%"));
            }
        }

        BigDecimal avgTicketChange = averageChange(dashboard.getBranchTrends(), BranchTrend::getAvgTicketChangePct);
        if (avgTicketChange != null && avgTicketChange.compareTo(BigDecimal.valueOf(-8)) < 0) {
            items.add(rec("brand-ticket", "SALES", "MEDIUM",
                    "Average ticket size is trending down",
                    "Brand avg ticket fell " + avgTicketChange.abs() + "% in the recent period. Push premium services and add-on treatments chain-wide.",
                    null, null, "Avg ticket trend", avgTicketChange + "%"));
        }

        if (dashboard.getTopStaff().size() >= 2) {
            BigDecimal topRev = dashboard.getTopStaff().get(0).getRevenue();
            BigDecimal totalStaffRev = dashboard.getTopStaff().stream()
                    .map(StaffStats::getRevenue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalStaffRev.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal share = topRev.multiply(BigDecimal.valueOf(100))
                        .divide(totalStaffRev, 0, RoundingMode.HALF_UP);
                if (share.compareTo(BigDecimal.valueOf(45)) > 0) {
                    items.add(rec("brand-staff-skew", "STAFF", "MEDIUM",
                            "Revenue concentrated among few stylists",
                            dashboard.getTopStaff().get(0).getStaffName() + " drives " + share
                                    + "% of tracked staff revenue. Cross-train and rotate high-value services across branches.",
                            null, null, "Top stylist share", share + "%"));
                }
            }
        }

        PaymentMix mix = dashboard.getPaymentMix();
        BigDecimal totalPay = mix.getCash().add(mix.getUpi()).add(mix.getCard());
        if (totalPay.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal cashPct = mix.getCash().multiply(BigDecimal.valueOf(100))
                    .divide(totalPay, 0, RoundingMode.HALF_UP);
            if (cashPct.compareTo(BigDecimal.valueOf(65)) > 0) {
                items.add(rec("brand-cash", "PAYMENTS", "LOW",
                        "Heavy cash dependency across brand",
                        cashPct + "% of payments are cash. Promote UPI/card to improve reconciliation and reduce cash-handling risk.",
                        null, null, "Cash share", cashPct + "%"));
            }
        }

        return items;
    }

    private List<Recommendation> generateBranchRecommendations(
            BranchStats branch,
            BranchTrend trend,
            List<StaffStats> branchStaff,
            List<ServiceStats> branchServices,
            DashboardResponse dashboard) {

        List<Recommendation> items = new ArrayList<>();
        UUID branchId = branch.getBranchId();
        String branchName = branch.getBranchName();

        if (trend != null) {
            addTrendRecommendation(items, trend.getRevenueChangePct(), "revenue", "SALES", "HIGH",
                    "Revenue is declining", "Revenue dropped %s%% vs the prior half of the period. Run a weekday promotion or re-engage dormant society residents.",
                    branchId, branchName);
            addTrendRecommendation(items, trend.getVisitsChangePct(), "visits", "OPERATIONS", "HIGH",
                    "Footfall is slowing", "Visits fell %s%% recently. Increase walk-in visibility, partner with society admins, or offer referral incentives.",
                    branchId, branchName);
            addTrendRecommendation(items, trend.getAvgTicketChangePct(), "avg ticket", "SALES", "MEDIUM",
                    "Average ticket size is down", "Avg ticket declined %s%%. Suggest add-on services (hair spa, conditioning) during checkout.",
                    branchId, branchName);
            addTrendRecommendation(items, trend.getDiscountsChangePct(), "discounts", "DISCOUNTS", "MEDIUM",
                    "Discount usage is rising", "Discounts increased %s%%. Review who approves bill-level discounts and set daily caps.",
                    branchId, branchName, true);
        }

        if (branch.getVisits() == 0) {
            items.add(rec(branchId + "-no-visits", "OPERATIONS", "HIGH",
                    "No completed visits in period",
                    "This branch has zero billed visits for the selected period. Verify staff are logging walk-ins and payments.",
                    branchId, branchName, "Visits", "0"));
        } else if (branch.getRevenue().compareTo(BigDecimal.ZERO) > 0
                && branch.getDiscountAmount() != null) {
            BigDecimal discountShare = branch.getDiscountAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(branch.getRevenue(), 1, RoundingMode.HALF_UP);
            if (discountShare.compareTo(BigDecimal.valueOf(15)) > 0) {
                items.add(rec(branchId + "-discount-share", "DISCOUNTS", "MEDIUM",
                        "High discount-to-revenue ratio",
                        "Discounts are " + discountShare + "% of branch revenue. Focus on full-price packages and loyalty offers instead of flat cuts.",
                        branchId, branchName, "Discount share", discountShare + "%"));
            }
        }

        if (branchStaff.size() >= 2) {
            BigDecimal total = branchStaff.stream().map(StaffStats::getRevenue).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal topShare = branchStaff.get(0).getRevenue()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(total, 0, RoundingMode.HALF_UP);
            if (topShare.compareTo(BigDecimal.valueOf(55)) > 0) {
                items.add(rec(branchId + "-staff-skew", "STAFF", "MEDIUM",
                        "Workload skewed to one stylist",
                        branchStaff.get(0).getStaffName() + " accounts for " + topShare
                                + "% of branch service revenue. Balance assignments and upskill other staff on top services.",
                        branchId, branchName, "Top stylist share", topShare + "%"));
            }

            StaffStats laggard = branchStaff.get(branchStaff.size() - 1);
            BigDecimal laggardShare = laggard.getRevenue()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(total, 0, RoundingMode.HALF_UP);
            if (laggardShare.compareTo(BigDecimal.valueOf(15)) < 0 && branchStaff.size() >= 3) {
                items.add(rec(branchId + "-staff-coach", "STAFF", "LOW",
                        "Coach underperforming stylist",
                        laggard.getStaffName() + " has the lowest service revenue (" + formatCurrency(laggard.getRevenue())
                                + "). Pair with top performers and assign premium services.",
                        branchId, branchName, "Lowest stylist revenue", formatCurrency(laggard.getRevenue())));
            }
        } else if (branchStaff.size() == 1) {
            items.add(rec(branchId + "-single-staff", "STAFF", "INFO",
                    "Single active revenue contributor",
                    "Only " + branchStaff.get(0).getStaffName() + " is generating tracked service revenue. Add staff coverage for peak hours.",
                    branchId, branchName, "Active stylists", "1"));
        }

        if (!branchServices.isEmpty()) {
            ServiceStats topService = branchServices.get(0);
            BigDecimal serviceTotal = branchServices.stream()
                    .map(ServiceStats::getRevenue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal topServiceShare = topService.getRevenue()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(serviceTotal, 0, RoundingMode.HALF_UP);

            items.add(rec(branchId + "-top-service", "SERVICES", "INFO",
                    "Double down on top service",
                    topService.getServiceName() + " is the best seller (" + topService.getCount()
                            + " bookings). Feature it on walk-in screens and train all staff to suggest it.",
                    branchId, branchName, "Top service", topService.getServiceName()));

            if (branchServices.size() >= 3) {
                BigDecimal top3 = branchServices.stream().limit(3)
                        .map(ServiceStats::getRevenue)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal top3Share = top3.multiply(BigDecimal.valueOf(100))
                        .divide(serviceTotal, 0, RoundingMode.HALF_UP);
                if (top3Share.compareTo(BigDecimal.valueOf(75)) > 0) {
                    items.add(rec(branchId + "-service-mix", "SERVICES", "MEDIUM",
                            "Narrow service mix",
                            "Top 3 services drive " + top3Share + "% of revenue. Promote underused treatments to grow average ticket.",
                            branchId, branchName, "Top-3 share", top3Share + "%"));
                }
            }

            if (branchServices.size() >= 2) {
                ServiceStats underused = branchServices.get(branchServices.size() - 1);
                if (underused.getCount() <= 2 && serviceTotal.compareTo(BigDecimal.ZERO) > 0) {
                    items.add(rec(branchId + "-upsell", "SERVICES", "LOW",
                            "Upsell opportunity",
                            underused.getServiceName() + " has only " + underused.getCount()
                                    + " bookings. Create a combo offer with " + topService.getServiceName() + ".",
                            branchId, branchName, "Underused service", underused.getServiceName()));
                }
            }
        }

        if (dashboard.getBranchStats().size() >= 2 && branch.getVisits() > 0) {
            BigDecimal brandAvgTicket = dashboard.getAvgTicketSize();
            if (branch.getAvgTicket().compareTo(brandAvgTicket.multiply(BigDecimal.valueOf(0.85))) < 0) {
                items.add(rec(branchId + "-below-avg-ticket", "SALES", "MEDIUM",
                        "Below brand average ticket",
                        branchName + " avg ticket is " + formatCurrency(branch.getAvgTicket())
                                + " vs brand " + formatCurrency(brandAvgTicket)
                                + ". Push premium packages and multi-service bookings.",
                        branchId, branchName, "Avg ticket gap",
                        formatCurrency(brandAvgTicket.subtract(branch.getAvgTicket()))));
            }
        }

        return items.stream()
                .sorted(Comparator.comparingInt(r -> severityOrder(r.getSeverity())))
                .collect(Collectors.toList());
    }

    private void addTrendRecommendation(
            List<Recommendation> items,
            BigDecimal changePct,
            String metricName,
            String category,
            String severity,
            String title,
            String messageTemplate,
            UUID branchId,
            String branchName) {
        addTrendRecommendation(items, changePct, metricName, category, severity, title, messageTemplate, branchId, branchName, false);
    }

    private void addTrendRecommendation(
            List<Recommendation> items,
            BigDecimal changePct,
            String metricName,
            String category,
            String severity,
            String title,
            String messageTemplate,
            UUID branchId,
            String branchName,
            boolean positiveIsBad) {

        if (changePct == null) return;
        boolean triggered = positiveIsBad
                ? changePct.compareTo(BigDecimal.valueOf(15)) > 0
                : changePct.compareTo(BigDecimal.valueOf(-10)) < 0;
        if (!triggered) return;

        String formattedChange = (positiveIsBad ? "+" : "") + changePct.setScale(1, RoundingMode.HALF_UP) + "%";
        items.add(rec(branchId + "-" + metricName.replace(' ', '-'), category, severity, title,
                String.format(messageTemplate, changePct.abs().setScale(1, RoundingMode.HALF_UP)),
                branchId, branchName, capitalize(metricName) + " trend", formattedChange));
    }

    private BigDecimal averageChange(List<BranchTrend> trends,
                                     java.util.function.Function<BranchTrend, BigDecimal> getter) {
        List<BigDecimal> values = trends.stream().map(getter).filter(Objects::nonNull).collect(Collectors.toList());
        if (values.isEmpty()) return null;
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP);
    }

    private Recommendation rec(String id, String category, String severity, String title, String message,
                               UUID branchId, String branchName, String metricLabel, String metricValue) {
        return Recommendation.builder()
                .id(id)
                .category(category)
                .severity(severity)
                .title(title)
                .message(message)
                .branchId(branchId)
                .branchName(branchName)
                .metricLabel(metricLabel)
                .metricValue(metricValue)
                .build();
    }

    private int severityOrder(String severity) {
        return switch (severity) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            case "LOW" -> 2;
            default -> 3;
        };
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) return value;
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String formatCurrency(BigDecimal amount) {
        return "₹" + amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
}
