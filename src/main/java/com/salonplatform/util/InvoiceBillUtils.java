package com.salonplatform.util;

import com.salonplatform.domain.entity.Invoice;

import java.math.BigDecimal;

public final class InvoiceBillUtils {

    private InvoiceBillUtils() {}

    public record MembershipFeeView(BigDecimal amount, String label) {}

    public record PackageFeeView(BigDecimal amount, String label) {}

    public static PackageFeeView resolvePackageFee(Invoice invoice) {
        BigDecimal amount = invoice.getPackageFeeAmount() != null
                ? invoice.getPackageFeeAmount() : BigDecimal.ZERO;
        String label = invoice.getPackageFeeLabel();
        if (amount.compareTo(BigDecimal.ZERO) <= 0
                && label != null
                && !label.isBlank()) {
            BigDecimal gap = feeGap(invoice);
            if (gap.compareTo(BigDecimal.ZERO) > 0) {
                amount = gap;
            }
        }
        return new PackageFeeView(amount, label);
    }

    public static MembershipFeeView resolveMembershipFee(Invoice invoice) {
        BigDecimal amount = invoice.getMembershipFeeAmount() != null
                ? invoice.getMembershipFeeAmount() : BigDecimal.ZERO;
        String label = invoice.getMembershipFeeLabel();
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            return new MembershipFeeView(amount, label);
        }
        BigDecimal packageFee = invoice.getPackageFeeAmount() != null
                ? invoice.getPackageFeeAmount() : BigDecimal.ZERO;
        if (packageFee.compareTo(BigDecimal.ZERO) > 0) {
            return new MembershipFeeView(BigDecimal.ZERO, null);
        }
        String packageLabel = invoice.getPackageFeeLabel();
        if (packageLabel != null && !packageLabel.isBlank()) {
            return new MembershipFeeView(BigDecimal.ZERO, null);
        }
        BigDecimal gap = feeGap(invoice);
        if (gap.compareTo(BigDecimal.ZERO) > 0) {
            amount = gap;
            if (label == null || label.isBlank()) {
                label = invoice.getMembershipLabel() != null
                        ? "Membership · " + invoice.getMembershipLabel().replaceAll(" \\(−.*", "")
                        : "Membership card";
            }
        }
        return new MembershipFeeView(amount, label);
    }

    private static BigDecimal feeGap(Invoice invoice) {
        BigDecimal servicesTotal = invoice.getTaxableAmount()
                .add(invoice.getCgstAmount())
                .add(invoice.getSgstAmount());
        return invoice.getGrandTotal().subtract(servicesTotal);
    }
}
