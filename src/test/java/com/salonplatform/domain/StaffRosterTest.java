package com.salonplatform.domain;

import com.salonplatform.domain.entity.Staff;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class StaffRosterTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static Staff deactivatedOn(String isoInstant) {
        return Staff.builder().active(false).deactivatedAt(Instant.parse(isoInstant)).build();
    }

    @Test
    void activeStaffIsAlwaysOnRoster() {
        Staff staff = Staff.builder().active(true).build();
        assertTrue(staff.onRosterOnOrAfter(LocalDate.of(2030, 1, 1), IST));
        assertNull(staff.lastRosterDate(IST));
    }

    @Test
    void deactivatedStaffStaysInPeriodsThatStartOnOrBeforeDeactivationDay() {
        Staff staff = deactivatedOn("2026-10-10T06:00:00Z");
        assertTrue(staff.onRosterOnOrAfter(LocalDate.of(2026, 10, 1), IST));
        assertTrue(staff.onRosterOnOrAfter(LocalDate.of(2026, 10, 10), IST));
    }

    @Test
    void deactivatedStaffIsOutOfPeriodsThatStartAfterDeactivationDay() {
        Staff staff = deactivatedOn("2026-10-10T06:00:00Z");
        assertFalse(staff.onRosterOnOrAfter(LocalDate.of(2026, 10, 11), IST));
    }

    @Test
    void deactivationDayUsesBranchTimezoneNotUtc() {
        // 20:00 UTC on the 10th is already the 11th in IST.
        Staff staff = deactivatedOn("2026-10-10T20:00:00Z");
        assertEquals(LocalDate.of(2026, 10, 11), staff.lastRosterDate(IST));
    }

    @Test
    void inactiveWithoutTimestampIsTreatedAsGone() {
        Staff staff = Staff.builder().active(false).build();
        assertFalse(staff.onRosterOnOrAfter(LocalDate.of(2020, 1, 1), IST));
    }
}
