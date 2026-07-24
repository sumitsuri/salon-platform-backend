package com.salonplatform.dto.branch;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateBranchGeofenceRequest {
    @NotNull
    private Double latitude;
    @NotNull
    private Double longitude;
    @Min(50)
    @Max(500)
    private Integer geofenceRadiusMeters;
    @Min(0)
    @Max(120)
    private Integer attendanceGraceMinutes;
}
