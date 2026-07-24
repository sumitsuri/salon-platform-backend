package com.salonplatform.util;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.enums.GeoStatus;

public final class GeofenceUtil {

    private GeofenceUtil() {}

    public static GeoStatus evaluate(Double lat, Double lng, Branch branch) {
        if (lat == null || lng == null) {
            return GeoStatus.GPS_UNAVAILABLE;
        }
        if (branch.getLatitude() == null || branch.getLongitude() == null) {
            return GeoStatus.GPS_UNAVAILABLE;
        }
        int radius = branch.getGeofenceRadiusMeters() != null ? branch.getGeofenceRadiusMeters() : 150;
        double distance = haversineMeters(lat, lng, branch.getLatitude(), branch.getLongitude());
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
