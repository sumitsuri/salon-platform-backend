package com.salonplatform.forecast;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Revenue per day for one branch, from its first sale to its last recorded day, with zeros for days without sales. */
public record Series(UUID id, String name, LocalDate start, double[] revenue) {

    public LocalDate end() {
        return start.plusDays(revenue.length - 1L);
    }

    public int length() {
        return revenue.length;
    }

    /** Revenue on {@code day}; zero outside the recorded range. */
    public double at(LocalDate day) {
        long i = ChronoUnit.DAYS.between(start, day);
        return i < 0 || i >= revenue.length ? 0 : revenue[(int) i];
    }

    public boolean covers(LocalDate day) {
        return !day.isBefore(start) && !day.isAfter(end());
    }
}
