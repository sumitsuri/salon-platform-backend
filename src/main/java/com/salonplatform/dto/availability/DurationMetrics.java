package com.salonplatform.dto.availability;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DurationMetrics {
    private Integer sampleVisitCount;
    private Double avgVisitMinutes;
    private Double medianVisitMinutes;
    private List<StaffServiceDurationStat> byStaffService;
}
