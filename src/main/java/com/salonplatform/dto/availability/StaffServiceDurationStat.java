package com.salonplatform.dto.availability;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class StaffServiceDurationStat {
    private UUID staffId;
    private String staffName;
    private UUID serviceId;
    private String serviceName;
    private int sampleCount;
    private Double avgEstimatedMinutes;
    private Double avgActualMinutes;
}
