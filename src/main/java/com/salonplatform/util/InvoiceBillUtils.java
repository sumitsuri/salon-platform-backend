package com.salonplatform.util;

import com.salonplatform.domain.entity.Invoice;

import java.math.BigDecimal;

public final class InvoiceBillUtils {

    private InvoiceBillUtils() {}

    public record MembershipFeeView(BigDecimal amount, String label) {}

    public static MembershipFeeView resolveMembershipFee(Invoice invoice) {
        BigDecimal amount = invoice.getMembershipFeeAmount() != null
                ? invoice.getMembershipFeeAmount() : BigDecimal.ZERO;
        String label = invoice.getMembershipFeeLabel();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            BigDecimal servicesTotal = invoice.getTaxableAmount()
                    .add(invoice.getCgstAmount())
                    .add(invoice.getSgstAmount());
            BigDecimal gap = invoice.getGrandTotal().subtract(servicesTotal);
            if (gap.compareTo(BigDecimal.ZERO) > 0) {
                amount = gap;
                if (label == null || label.isBlank()) {
                    label = invoice.getMembershipLabel() != null
                            ? "Membership · " + invoice.getMembershipLabel().replaceAll(" \\(−.*", "")
                            : "Membership card";
                }
            }
        }
        return new MembershipFeeView(amount, label);
    }
}
