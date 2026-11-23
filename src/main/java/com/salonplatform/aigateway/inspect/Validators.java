package com.salonplatform.aigateway.inspect;

/** Checksums that separate real card and Aadhaar numbers from ordinary digit strings (amounts, ids, dates). */
public final class Validators {

    private Validators() {}

    /** Luhn check used by payment cards. */
    public static boolean luhn(String digits) {
        int sum = 0;
        boolean alt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (d < 0 || d > 9) return false;
            if (alt) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            alt = !alt;
        }
        return digits.length() >= 13 && sum % 10 == 0;
    }

    private static final int[][] VERHOEFF_D = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, {1, 2, 3, 4, 0, 6, 7, 8, 9, 5}, {2, 3, 4, 0, 1, 7, 8, 9, 5, 6},
            {3, 4, 0, 1, 2, 8, 9, 5, 6, 7}, {4, 0, 1, 2, 3, 9, 5, 6, 7, 8}, {5, 9, 8, 7, 6, 0, 4, 3, 2, 1},
            {6, 5, 9, 8, 7, 1, 0, 4, 3, 2}, {7, 6, 5, 9, 8, 2, 1, 0, 4, 3}, {8, 7, 6, 5, 9, 3, 2, 1, 0, 4},
            {9, 8, 7, 6, 5, 4, 3, 2, 1, 0}};
    private static final int[][] VERHOEFF_P = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, {1, 5, 7, 6, 2, 8, 3, 0, 9, 4}, {5, 8, 0, 3, 7, 9, 6, 1, 4, 2},
            {8, 9, 1, 6, 0, 4, 3, 5, 2, 7}, {9, 4, 5, 3, 1, 2, 6, 8, 7, 0}, {4, 2, 8, 6, 5, 7, 3, 9, 0, 1},
            {2, 7, 9, 3, 8, 0, 6, 4, 1, 5}, {7, 0, 4, 6, 9, 1, 3, 2, 5, 8}};

    /** Verhoeff check used by the 12-digit Aadhaar number. */
    public static boolean verhoeff(String digits) {
        if (digits.length() != 12) return false;
        int c = 0;
        for (int i = 0; i < digits.length(); i++) {
            int d = digits.charAt(digits.length() - 1 - i) - '0';
            if (d < 0 || d > 9) return false;
            c = VERHOEFF_D[c][VERHOEFF_P[i % 8][d]];
        }
        return c == 0;
    }

    /** A credential-looking value: has a digit or symbol, or mixes upper and lower case. */
    public static boolean looksLikeSecretValue(String v) {
        boolean digit = false, symbol = false, upper = false, lower = false;
        for (char ch : v.toCharArray()) {
            if (Character.isDigit(ch)) digit = true;
            else if (Character.isUpperCase(ch)) upper = true;
            else if (Character.isLowerCase(ch)) lower = true;
            else symbol = true;
        }
        return digit || symbol || (upper && lower);
    }
}
