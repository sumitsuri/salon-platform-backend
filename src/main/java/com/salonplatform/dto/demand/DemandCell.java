package com.salonplatform.dto.demand;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Totals for one weekday + hour over the period. Divide by the weekday's occurrences for per-day averages. */
@Data
@NoArgsConstructor
public class DemandCell {
    /** ISO weekday, 1 = Monday … 7 = Sunday. */
    private int dow;
    /** Hour of day, 0-23, in the branch's local time. */
    private int hour;
    /** Customers who arrived (not cancelled). */
    private long visits;
    private long cancelled;
    private long walkIns;
    private long online;
    /** Billed amount of those visits (₹, voided invoices excluded). */
    private double revenue;
    /** Staff-minutes on the floor in this hour, summed over the period (from attendance). */
    private double staffMinutes;
    /** STRETCHED | OK | IDLE for open hours when staffing is known (or judged from demand alone); otherwise null. */
    private String state;
    /** How busy each person on the floor is in this hour, in % (can exceed 100); null when attendance is unknown. */
    private Integer busyPercent;

    public DemandCell(int dow, int hour, long visits, long cancelled, long walkIns, long online, double revenue, double staffMinutes) {
        this.dow = dow;
        this.hour = hour;
        this.visits = visits;
        this.cancelled = cancelled;
        this.walkIns = walkIns;
        this.online = online;
        this.revenue = revenue;
        this.staffMinutes = staffMinutes;
    }
}
