package com.salonplatform.dto.staff;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StaffTargetPerformanceResponse {
    private String periodLabel;
    private int meetingTargetCount;
    private int belowTargetCount;
    private List<StaffTargetPerformanceItem> staff;
}
