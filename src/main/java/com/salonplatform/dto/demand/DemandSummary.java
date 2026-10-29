package com.salonplatform.dto.demand;

import lombok.Builder;
import lombok.Data;

/** The headline numbers for a branch. Fields are null when they cannot be worked out from the data. */
@Data
@Builder
public class DemandSummary {
    private double avgVisitsPerOpenDay;
    private double revenuePerVisit;
    /** Busiest single weekday-hour, e.g. "Sat 6-7 pm". */
    private String busiestSlot;
    private double busiestSlotVisitsPerHour;
    /** Busiest and quietest weekday by customers per day. */
    private String busiestDay;
    private String quietestDay;
    /** Busiest weekday ÷ quietest open weekday. */
    private Double dayPeakRatio;
    /** Overall busiest and quietest opening hour, e.g. "6-7 pm". */
    private String busiestHour;
    private String quietestHour;
    /** Share of customers who arrive when staffing is about right (null without attendance data). */
    private Integer staffingFitPercent;
    /** How busy the team on the floor is on average: customer service time ÷ staff time (null without attendance). */
    private Integer teamBusyPercent;
    /** Typical visit length used for that, in minutes. */
    private Double visitMinutes;
    /** Hours per week where the team is stretched / has spare people. */
    private Double stretchedHoursPerWeek;
    private Double spareStaffHoursPerWeek;
    /** ₹ per month of staff time spent idle in quiet hours (needs salaries); an estimate. */
    private Double idleStaffCostPerMonth;
    /** Customers over the last 28 days vs the 28 before, in % (null when too little history). */
    private Double trendPercent;
    /** Share of visits logged outside opening hours, in %. */
    private double outsideHoursPercent;
    private int openDays;
    private String closedDays;
}
