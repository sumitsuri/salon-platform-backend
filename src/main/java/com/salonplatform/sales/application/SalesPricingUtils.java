package com.salonplatform.sales.application;

import com.salonplatform.sales.domain.entity.SalesLead;
import com.salonplatform.sales.domain.enums.BillingPeriod;
import com.salonplatform.sales.dto.UpdateSalesLeadRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class SalesPricingUtils {

    private static final int MONEY_SCALE = 2;

    private SalesPricingUtils() {}

    public static BigDecimal monthlyEquivalent(BigDecimal amount, BillingPeriod period) {
        if (amount == null) {
            return null;
        }
        int months = monthsInPeriod(period);
        if (months <= 1) {
            return amount;
        }
        return amount.divide(BigDecimal.valueOf(months), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal effectiveRevenueAmount(SalesLead lead) {
        if (lead.getFinalPaidAmount() != null) {
            return lead.getFinalPaidAmount();
        }
        return lead.getQuotedAmount();
    }

    public static BigDecimal monthlyRevenue(SalesLead lead) {
        return monthlyEquivalent(effectiveRevenueAmount(lead), lead.getBillingPeriod());
    }

    public static void syncPricing(SalesLead lead, UpdateSalesLeadRequest request) {
        if (request.getQuotedAmount() != null) {
            lead.setQuotedAmount(request.getQuotedAmount());
        }
        if (request.getBillingPeriod() != null) {
            lead.setBillingPeriod(request.getBillingPeriod());
        }

        BigDecimal quoted = lead.getQuotedAmount();
        if (quoted == null || quoted.signum() <= 0) {
            lead.setDiscountPercent(null);
            lead.setDiscountAmount(null);
            lead.setFinalPaidAmount(null);
            lead.setProjectedMrr(null);
            return;
        }

        if (request.getFinalPaidAmount() != null) {
            applyFromFinalPaid(lead, quoted, request.getFinalPaidAmount());
        } else if (request.getDiscountAmount() != null) {
            applyFromDiscountAmount(lead, quoted, request.getDiscountAmount());
        } else if (request.getDiscountPercent() != null) {
            applyFromDiscountPercent(lead, quoted, request.getDiscountPercent());
        } else if (request.getQuotedAmount() != null) {
            recalcFromExistingDiscount(lead, quoted);
        }

        lead.setProjectedMrr(monthlyEquivalent(effectiveRevenueAmount(lead), lead.getBillingPeriod()));
    }

    private static void applyFromFinalPaid(SalesLead lead, BigDecimal quoted, BigDecimal finalPaid) {
        BigDecimal capped = finalPaid.max(BigDecimal.ZERO).min(quoted);
        lead.setFinalPaidAmount(capped);
        BigDecimal discount = quoted.subtract(capped).max(BigDecimal.ZERO);
        lead.setDiscountAmount(discount);
        lead.setDiscountPercent(percentOf(quoted, discount));
    }

    private static void applyFromDiscountAmount(SalesLead lead, BigDecimal quoted, BigDecimal discountAmount) {
        BigDecimal discount = discountAmount.max(BigDecimal.ZERO).min(quoted);
        lead.setDiscountAmount(discount);
        lead.setDiscountPercent(percentOf(quoted, discount));
        lead.setFinalPaidAmount(quoted.subtract(discount));
    }

    private static void applyFromDiscountPercent(SalesLead lead, BigDecimal quoted, BigDecimal discountPercent) {
        BigDecimal pct = discountPercent.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
        lead.setDiscountPercent(pct);
        BigDecimal discount = quoted.multiply(pct)
                .divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP);
        lead.setDiscountAmount(discount);
        lead.setFinalPaidAmount(quoted.subtract(discount).max(BigDecimal.ZERO));
    }

    private static void recalcFromExistingDiscount(SalesLead lead, BigDecimal quoted) {
        if (lead.getDiscountPercent() != null) {
            applyFromDiscountPercent(lead, quoted, lead.getDiscountPercent());
        } else if (lead.getDiscountAmount() != null) {
            applyFromDiscountAmount(lead, quoted, lead.getDiscountAmount());
        } else {
            lead.setFinalPaidAmount(quoted);
            lead.setDiscountAmount(BigDecimal.ZERO);
            lead.setDiscountPercent(BigDecimal.ZERO);
        }
    }

    private static BigDecimal percentOf(BigDecimal quoted, BigDecimal part) {
        if (quoted.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(BigDecimal.valueOf(100))
                .divide(quoted, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    static int monthsInPeriod(BillingPeriod period) {
        if (period == null) {
            return 1;
        }
        return switch (period) {
            case YEARLY -> 12;
            case HALF_YEARLY -> 6;
            case QUARTERLY -> 3;
            case MONTHLY -> 1;
        };
    }
}
