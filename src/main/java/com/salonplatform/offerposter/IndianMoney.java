package com.salonplatform.offerposter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Rupee formatting with Indian digit grouping (₹1,99,999) and tidy percentages. */
public final class IndianMoney {

    private IndianMoney() {}

    public static String rupees(BigDecimal amount) {
        if (amount == null) return "₹0";
        BigDecimal whole = amount.setScale(0, RoundingMode.HALF_UP);
        String digits = whole.abs().toPlainString();
        String grouped;
        if (digits.length() <= 3) {
            grouped = digits;
        } else {
            String last3 = digits.substring(digits.length() - 3);
            String rest = digits.substring(0, digits.length() - 3);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < rest.length(); i++) {
                if (i > 0 && (rest.length() - i) % 2 == 0) sb.append(',');
                sb.append(rest.charAt(i));
            }
            grouped = sb + "," + last3;
        }
        return (whole.signum() < 0 ? "-₹" : "₹") + grouped;
    }

    /** 30 → "30", 12.5 → "12.5". */
    public static String percent(BigDecimal pct) {
        if (pct == null) return "0";
        BigDecimal stripped = pct.stripTrailingZeros();
        return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
    }
}
