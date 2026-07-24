package com.salonplatform.util;

public final class PhoneUtils {

    private PhoneUtils() {}

    /** Normalize to MSG91 format: 91 + 10-digit Indian mobile. */
    public static String normalizeIndianMobile(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() == 10) {
            return "91" + digits;
        }
        if (digits.startsWith("91") && digits.length() == 12) {
            return digits;
        }
        if (digits.startsWith("0") && digits.length() == 11) {
            return "91" + digits.substring(1);
        }
        return digits;
    }
}
