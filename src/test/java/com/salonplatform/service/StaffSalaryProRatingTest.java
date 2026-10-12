package com.salonplatform.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StaffSalaryProRatingTest {

    @Test
    void proRatesByPresentPlusLeaveOverMonthLength() {
        BigDecimal salary = new BigDecimal("30000");
        BigDecimal result = StaffSalaryProRating.proRated(salary, 10, 2, LocalDate.of(2026, 10, 13));
        assertEquals(new BigDecimal("11612.90"), result);
    }
}
