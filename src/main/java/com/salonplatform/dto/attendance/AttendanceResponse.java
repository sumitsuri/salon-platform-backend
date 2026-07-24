package com.salonplatform.dto.attendance;

import com.salonplatform.domain.enums.AttendanceMethod;
import com.salonplatform.domain.enums.GeoStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class AttendanceResponse {
    private UUID id;
    private UUID staffId;
    private String staffName;
    private UUID branchId;
    private String branchName;
    private LocalDate workDate;
    private Instant entryTime;
    private Instant exitTime;
    private AttendanceMethod entryMethod;
    private AttendanceMethod exitMethod;
    private String manualReason;
    private Double hoursWorked;
    private String status;
    private GeoStatus entryGeoStatus;
    private GeoStatus exitGeoStatus;
    private Boolean entryVerified;
    private Boolean exitVerified;
    private Boolean hasEntryPhoto;
    private Boolean hasExitPhoto;
    private Boolean late;
    private Boolean earlyExit;
    private Integer lateMinutes;
    private Integer earlyExitMinutes;
    private List<String> complianceFlags;
    /** Branch geofence centre (expected location). */
    private Double branchLatitude;
    private Double branchLongitude;
    private Integer geofenceRadiusMeters;
    /** Actual GPS at punch. */
    private Double entryLatitude;
    private Double entryLongitude;
    private Double exitLatitude;
    private Double exitLongitude;
    /** Distance from branch centre in metres (null if coords missing). */
    private Double entryDistanceMeters;
    private Double exitDistanceMeters;
}
