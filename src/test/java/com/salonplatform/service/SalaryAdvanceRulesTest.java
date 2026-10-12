package com.salonplatform.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SalaryAdvanceRulesTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void availableIsPoolMinusOutstandingAndNeverNegative() {
        assertEquals(bd("60000.00"), SalaryAdvanceRules.poolAvailable(bd("100000"), bd("40000")));
        assertEquals(bd("0.00"), SalaryAdvanceRules.poolAvailable(bd("30000"), bd("40000")),
                "lowering the pool below what is out must not produce a negative balance");
    }

    @Test
    void perEmployeeCapIsTheShareOfThePool() {
        assertEquals(bd("25000.00"), SalaryAdvanceRules.perEmployeeCap(bd("100000"), 25));
    }

    @Test
    void eligibleMaxIsTheSmallestOfPoolShareAndSalaryRoom() {
        // pool 1,00,000, share 25% -> cap 25,000; salary 20,000 x 1.0 -> 20,000 is the binding limit
        assertEquals(bd("20000.00"),
                SalaryAdvanceRules.eligibleMax(bd("100000"), bd("0"), bd("0"), bd("20000"), 25, bd("1")));
        // salary room is large, so the share cap binds
        assertEquals(bd("25000.00"),
                SalaryAdvanceRules.eligibleMax(bd("100000"), bd("0"), bd("0"), bd("90000"), 25, bd("1")));
        // little left in the pool: the pool binds
        assertEquals(bd("5000.00"),
                SalaryAdvanceRules.eligibleMax(bd("100000"), bd("95000"), bd("0"), bd("90000"), 25, bd("1")));
    }

    @Test
    void whatAnEmployeeAlreadyHoldsCountsAgainstTheirLimits() {
        // already holds 15,000 of a 25,000 cap -> only 10,000 more
        assertEquals(bd("10000.00"),
                SalaryAdvanceRules.eligibleMax(bd("100000"), bd("15000"), bd("15000"), bd("90000"), 25, bd("1")));
        // at the cap -> nothing more, which is what stops one person using the whole budget
        assertEquals(bd("0.00"),
                SalaryAdvanceRules.eligibleMax(bd("100000"), bd("25000"), bd("25000"), bd("90000"), 25, bd("1")));
    }

    @Test
    void missingSalarySkipsOnlyTheSalaryLimit() {
        assertEquals(bd("25000.00"),
                SalaryAdvanceRules.eligibleMax(bd("100000"), bd("0"), bd("0"), null, 25, bd("1")));
    }

    @Test
    void eligibleMaxIsRoundedDownToWholeRupees() {
        assertEquals(bd("33333.00"),
                SalaryAdvanceRules.eligibleMax(bd("100000"), bd("0"), bd("0"), bd("33333.99"), 100, bd("1")));
    }

    @Test
    void installmentsAddUpExactlyAndTheLastOneAbsorbsTheRemainder() {
        List<BigDecimal> parts = SalaryAdvanceRules.splitInstallments(bd("10000"), 3);
        assertEquals(3, parts.size());
        assertEquals(bd("3333.33"), parts.get(0));
        assertEquals(bd("3333.33"), parts.get(1));
        assertEquals(bd("3333.34"), parts.get(2));
        assertEquals(bd("10000.00"), parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void singleInstallmentIsTheWholeAmount() {
        assertEquals(List.of(bd("5000.00")), SalaryAdvanceRules.splitInstallments(bd("5000"), 1));
    }

    @Test
    void zeroInstallmentsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SalaryAdvanceRules.splitInstallments(bd("100"), 0));
    }

    @Test
    void firstDeductionDefaultsToNextMonthIncludingYearEnd() {
        assertEquals(LocalDate.of(2026, 11, 1), SalaryAdvanceRules.firstOfNextMonth(LocalDate.of(2026, 10, 13)));
        assertEquals(LocalDate.of(2027, 1, 1), SalaryAdvanceRules.firstOfNextMonth(LocalDate.of(2026, 12, 31)));
    }

    @Test
    void cooldownBlocksUntilTheEndDateThenClears() {
        Instant last = LocalDate.of(2026, 10, 1).atTime(10, 0).atZone(IST).toInstant();
        LocalDate ends = SalaryAdvanceRules.cooldownEndsOn(last, 30, IST, LocalDate.of(2026, 10, 13));
        assertEquals(LocalDate.of(2026, 10, 31), ends);
        assertNull(SalaryAdvanceRules.cooldownEndsOn(last, 30, IST, LocalDate.of(2026, 10, 31)), "clears on the end date");
        assertNull(SalaryAdvanceRules.cooldownEndsOn(null, 30, IST, LocalDate.of(2026, 10, 13)));
        assertNull(SalaryAdvanceRules.cooldownEndsOn(last, 0, IST, LocalDate.of(2026, 10, 2)), "zero days means no cooldown");
    }

    @Test
    void repeatRequesterStartsAtThreeRequests() {
        assertFalse(SalaryAdvanceRules.isRepeatRequester(2));
        assertTrue(SalaryAdvanceRules.isRepeatRequester(3));
    }

    @Test
    void percentOfHandlesEmptyPoolAndCapsAtHundred() {
        assertEquals(0, SalaryAdvanceRules.percentOf(bd("500"), bd("0")));
        assertEquals(25, SalaryAdvanceRules.percentOf(bd("25000"), bd("100000")));
        assertEquals(100, SalaryAdvanceRules.percentOf(bd("150000"), bd("100000")));
    }
}
