package com.salonplatform.dto.demand;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonthStat {
    /** yyyy-MM */
    private String month;
    private long visits;
    private double revenue;
    /** Days in the month that had at least one visit. */
    private int activeDays;
    private double visitsPerDay;
    /** The month still in progress (its average is based on fewer days). */
    private boolean partial;
}
