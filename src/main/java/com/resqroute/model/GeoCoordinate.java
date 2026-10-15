package com.resqroute.model;

import java.util.Objects;

/**
 * Immutable geographic coordinates representing a location on Earth using WGS84 latitude and longitude.
 *
 * @param latitude  Latitude in degrees, between -90.0 and +90.0 inclusive.
 * @param longitude Longitude in degrees, between -180.0 and +180.0 inclusive.
 */
public record GeoCoordinate(double latitude, double longitude) {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    public GeoCoordinate {
        if (Double.isNaN(latitude) || Double.isInfinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90.0 and +90.0 degrees: " + latitude);
        }
        if (Double.isNaN(longitude) || Double.isInfinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180.0 and +180.0 degrees: " + longitude);
        }
    }

    /**
     * Calculates great-circle distance to another coordinate using the Haversine formula.
     *
     * @param other Target geographic coordinate.
     * @return Distance in kilometres.
     */
    public double haversineDistanceKm(GeoCoordinate other) {
        Objects.requireNonNull(other, "Target coordinate must not be null");
        double dLat = Math.toRadians(other.latitude - this.latitude);
        double dLon = Math.toRadians(other.longitude - this.longitude);
        double lat1 = Math.toRadians(this.latitude);
        double lat2 = Math.toRadians(other.latitude);

        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_KM * c;
    }
}
