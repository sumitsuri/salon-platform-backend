package com.salonplatform.dto.demand;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BranchDemand {
    private UUID branchId;
    private String branchName;
    private String openTime;
    private String closeTime;
    /** First open hour and the hour it closes (exclusive), e.g. 10 and 21 for 10:00-21:00. */
    private int openHour;
    private int closeHour;
    private long totalVisits;
    private long totalCancelled;
    private double totalRevenue;
    /** How many of each ISO weekday (index 0 = Monday) fall in the period for this branch. */
    private int[] dowOccurrences;
    private List<DemandCell> cells;
    /** True when attendance data is good enough to compare demand with staffing. */
    private boolean staffDataAvailable;
    /** Customers one person can comfortably serve in an hour (at ~70% busy), from the typical visit length. */
    private Double customersPerStaffHour;
    /** Typical visit length in minutes and where it came from: recorded | estimated | assumed. */
    private Double visitMinutes;
    private String visitMinutesSource;
    private List<MonthStat> months;
    /** False when there is too little history for advice to be trustworthy. */
    private boolean enoughData;
    private String dataNote;
    private DemandSummary summary;
    private List<DemandRecommendation> recommendations;
}
