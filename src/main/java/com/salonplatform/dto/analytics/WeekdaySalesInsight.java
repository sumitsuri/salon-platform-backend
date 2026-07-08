package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class WeekdaySalesInsight {
    private UUID branchId;
    private String branchName;
    private List<DayOfWeekStat> dayStats;
    private List<SlowDayAction> slowDayActions;
}
