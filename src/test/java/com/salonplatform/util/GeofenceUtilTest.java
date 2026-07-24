package com.salonplatform.util;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.enums.GeoStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeofenceUtilTest {

    private static Branch lithosBranch() {
        return Branch.builder()
                .latitude(12.9352)
                .longitude(77.6245)
                .geofenceRadiusMeters(150)
                .build();
    }

    @Test
    void suspiciousHighAccuracyDesktopFixNeverFlagsOut() {
        // macOS Chrome: enableHighAccuracy returns km-off Wi‑Fi fix with ±16m accuracy
        GeoStatus status = GeofenceUtil.evaluate(
                12.93243, 77.74613, 16.0, true, lithosBranch());
        assertEquals(GeoStatus.GPS_UNAVAILABLE, status);
    }

    @Test
    void networkLocationNeverFlagsOut() {
        // Bad Wi‑Fi/IP fix ~13km away (reported in localhost RCA)
        GeoStatus status = GeofenceUtil.evaluate(
                12.93241, 77.74613, 17.0, false, lithosBranch());
        assertEquals(GeoStatus.GPS_UNAVAILABLE, status);
    }

    @Test
    void highAccuracyInsideGeofence() {
        GeoStatus status = GeofenceUtil.evaluate(
                12.93525, 77.62455, 15.0, true, lithosBranch());
        assertEquals(GeoStatus.IN_GEOFENCE, status);
    }

    @Test
    void highAccuracyClearlyOutsideGeofence() {
        // ~400m from branch with genuine GPS accuracy — should flag OUT
        GeoStatus status = GeofenceUtil.evaluate(
                12.9316, 77.6245, 20.0, true, lithosBranch());
        assertEquals(GeoStatus.OUT_OF_GEOFENCE, status);
    }

    @Test
    void lowAccuracyDoesNotFlagOut() {
        GeoStatus status = GeofenceUtil.evaluate(
                12.93525, 77.62455, 500.0, true, lithosBranch());
        assertEquals(GeoStatus.GPS_UNAVAILABLE, status);
    }
}
