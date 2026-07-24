package com.salonplatform.util;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.enums.GeoStatus;

public final class GeofenceUtil {

    /** Coarse / network fixes above this are not trusted for geofence OUT. */
    public static final double MAX_GEOFENCE_ACCURACY_METERS = 200;
    /** Distance above which a tight accuracy reading is treated as unreliable (desktop Wi‑Fi lie). */
    public static final double SUSPICIOUS_GEOFENCE_DISTANCE_METERS = 500;

    private GeofenceUtil() {}

    static boolean isSuspiciousGeofenceFix(double distanceMeters, Double accuracyMeters, int radius) {
        if (accuracyMeters == null || accuracyMeters <= 0) {
            return false;
        }
        double threshold = Math.max(SUSPICIOUS_GEOFENCE_DISTANCE_METERS, radius * 3.0);
        return distanceMeters > threshold && accuracyMeters < distanceMeters / 10.0;
    }

    public static GeoStatus evaluate(Double lat, Double lng, Branch branch) {
        return evaluate(lat, lng, null, null, branch);
    }

    public static GeoStatus evaluate(Double lat, Double lng, Double accuracyMeters, Boolean locationHighAccuracy, Branch branch) {
        if (lat == null || lng == null) {
            return GeoStatus.GPS_UNAVAILABLE;
        }
        if (branch.getLatitude() == null || branch.getLongitude() == null) {
            return GeoStatus.GPS_UNAVAILABLE;
        }
        // Wi‑Fi / IP geolocation from desktop browsers — do not flag OUT (multi-km false positives).
        if (Boolean.FALSE.equals(locationHighAccuracy)) {
            return GeoStatus.GPS_UNAVAILABLE;
        }
        int radius = branch.getGeofenceRadiusMeters() != null ? branch.getGeofenceRadiusMeters() : 150;
        double distance = haversineMeters(lat, lng, branch.getLatitude(), branch.getLongitude());
        if (accuracyMeters != null && accuracyMeters > MAX_GEOFENCE_ACCURACY_METERS) {
            return GeoStatus.GPS_UNAVAILABLE;
        }
        if (isSuspiciousGeofenceFix(distance, accuracyMeters, radius)) {
            return GeoStatus.GPS_UNAVAILABLE;
        }
        if (accuracyMeters != null) {
            if (distance + accuracyMeters <= radius) {
                return GeoStatus.IN_GEOFENCE;
            }
            if (distance - accuracyMeters > radius) {
                return GeoStatus.OUT_OF_GEOFENCE;
            }
            return GeoStatus.GPS_UNAVAILABLE;
        }
        return distance <= radius ? GeoStatus.IN_GEOFENCE : GeoStatus.OUT_OF_GEOFENCE;
    }

    public static Double distanceMeters(Double lat, Double lng, Branch branch) {
        if (lat == null || lng == null || branch == null
                || branch.getLatitude() == null || branch.getLongitude() == null) {
            return null;
        }
        return haversineMeters(lat, lng, branch.getLatitude(), branch.getLongitude());
    }

    /** Haversine distance in meters. */
    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
