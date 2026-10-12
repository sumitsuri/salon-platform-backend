package com.salonplatform.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Monthly salary pro-rated by days present plus approved leave in a period (calendar-month denominator). */
public final class StaffSalaryProRating {

    private StaffSalaryProRating() {}

    /**
     * {@code monthlySalary × (daysPresent + approvedLeaveDays) / daysInMonth}, rounded to paise.
     * Denominator is the length of the month containing {@code periodEnd}.
     */
    public static BigDecimal proRated(
            BigDecimal monthlySalary,
            long daysPresent,
            double approvedLeaveDays,
            LocalDate periodEnd) {
        if (monthlySalary == null || monthlySalary.signum() <= 0 || periodEnd == null) {
            return null;
        }
        int daysInMonth = periodEnd.lengthOfMonth();
        if (daysInMonth <= 0) {
            return null;
        }
        double payableDays = daysPresent + approvedLeaveDays;
        if (payableDays <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return monthlySalary
                .multiply(BigDecimal.valueOf(payableDays))
                .divide(BigDecimal.valueOf(daysInMonth), 2, RoundingMode.HALF_UP);
    }
}
