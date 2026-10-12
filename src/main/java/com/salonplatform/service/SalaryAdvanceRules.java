package com.salonplatform.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * The arithmetic behind salary advances, kept free of persistence so the rules that protect the shared budget
 * can be tested exactly.
 */
final class SalaryAdvanceRules {

    /** Requests in this many days count towards the "repeat requester" signal. */
    static final int REPEAT_WINDOW_DAYS = 180;
    static final int REPEAT_THRESHOLD = 3;

    private SalaryAdvanceRules() {}

    static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    /** Budget not yet committed. Never negative, even if the pool is later lowered below what is out. */
    static BigDecimal poolAvailable(BigDecimal pool, BigDecimal outstandingTotal) {
        return money(money(pool).subtract(money(outstandingTotal)).max(BigDecimal.ZERO));
    }

    /** The most a single employee may hold at once: the pool times the per-employee share limit. */
    static BigDecimal perEmployeeCap(BigDecimal pool, int maxSharePercent) {
        return money(pool).multiply(BigDecimal.valueOf(maxSharePercent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
    }

    /**
     * What an employee can be granted right now: the smallest of what is left in the pool, what they may still hold
     * under the share limit, and what they may still hold relative to their salary. Rounded down to whole rupees.
     *
     * @param monthlySalary null when no salary is on record — the salary limit is then skipped
     */
    static BigDecimal eligibleMax(
            BigDecimal pool,
            BigDecimal outstandingTotal,
            BigDecimal staffOutstanding,
            BigDecimal monthlySalary,
            int maxSharePercent,
            BigDecimal maxSalaryMultiple) {
        BigDecimal available = poolAvailable(pool, outstandingTotal);
        BigDecimal shareRoom = perEmployeeCap(pool, maxSharePercent).subtract(money(staffOutstanding)).max(BigDecimal.ZERO);
        BigDecimal result = available.min(shareRoom);
        if (monthlySalary != null && monthlySalary.signum() > 0 && maxSalaryMultiple != null) {
            BigDecimal salaryRoom = monthlySalary.multiply(maxSalaryMultiple).subtract(money(staffOutstanding)).max(BigDecimal.ZERO);
            result = result.min(salaryRoom);
        }
        return result.setScale(0, RoundingMode.DOWN).setScale(2);
    }

    /** Splits an amount into equal monthly installments; the last one absorbs the rounding remainder. */
    static List<BigDecimal> splitInstallments(BigDecimal amount, int installments) {
        if (installments < 1) {
            throw new IllegalArgumentException("installments must be at least 1");
        }
        BigDecimal total = money(amount);
        BigDecimal each = total.divide(BigDecimal.valueOf(installments), 2, RoundingMode.DOWN);
        List<BigDecimal> out = new ArrayList<>(installments);
        BigDecimal running = BigDecimal.ZERO;
        for (int i = 0; i < installments - 1; i++) {
            out.add(each);
            running = running.add(each);
        }
        out.add(total.subtract(running));
        return out;
    }

    static LocalDate startOfMonth(LocalDate date) {
        return date.withDayOfMonth(1);
    }

    /** Salary month the first deduction defaults to: the month after the decision. */
    static LocalDate firstOfNextMonth(LocalDate today) {
        return startOfMonth(today).plusMonths(1);
    }

    /** First day a new request is allowed, or null if the cooldown has passed (or there was no earlier request). */
    static LocalDate cooldownEndsOn(Instant lastRequestAt, int cooldownDays, ZoneId zone, LocalDate today) {
        if (lastRequestAt == null || cooldownDays <= 0) {
            return null;
        }
        LocalDate ends = lastRequestAt.atZone(zone).toLocalDate().plusDays(cooldownDays);
        return today.isBefore(ends) ? ends : null;
    }

    static boolean isRepeatRequester(int requestsInWindow) {
        return requestsInWindow >= REPEAT_THRESHOLD;
    }

    static int percentOf(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() <= 0) {
            return 0;
        }
        return Math.min(100, part.multiply(BigDecimal.valueOf(100)).divide(whole, 0, RoundingMode.HALF_UP).intValue());
    }
}
